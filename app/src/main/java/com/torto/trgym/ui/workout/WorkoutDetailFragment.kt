
package com.torto.trgym.ui.workout

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutExercisesAdapter
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.WorkoutSession
import android.widget.ImageView
import com.torto.trgym.ui.dialogs.SelectExercisesDialog
import com.torto.trgym.ui.helpers.ExerciseDragCallback
import com.torto.trgym.services.HeartRateManager
import com.torto.trgym.viewmodel.workout.WorkoutDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutDetailFragment : Fragment(R.layout.fragment_workout_detail) {

    private val workoutDetailViewModel: WorkoutDetailViewModel by viewModels()
    private lateinit var exercisesAdapter: WorkoutExercisesAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var dragCallback: ExerciseDragCallback
    
    @Inject
    lateinit var heartRateManager: HeartRateManager

    // Views - simplified with lazy initialization
    private val backButton by lazy { requireView().findViewById<TextView>(R.id.backButton) }
    private val workoutTitle by lazy { requireView().findViewById<TextView>(R.id.workoutTitle) }
    private val editButton by lazy { requireView().findViewById<TextView>(R.id.editButton) }
    private val exercisesRecyclerView by lazy { requireView().findViewById<RecyclerView>(R.id.exercisesRecyclerView) }
    private val emptyStateLayout by lazy { requireView().findViewById<View>(R.id.emptyStateLayout) }
    private val startWorkoutButton by lazy { requireView().findViewById<MaterialButton>(R.id.startWorkoutButton) }
    private val addExerciseBtn by lazy { requireView().findViewById<FloatingActionButton>(R.id.addExerciseBtn) }
    private val exercisePreviewIcon by lazy { requireView().findViewById<ImageView>(R.id.exercisePreviewIcon) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()

        // Setup back press handler
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        workoutDetailViewModel.refreshWorkoutCard()
    }

    // ==========================================
    // SETUP WITH DRAG AND DROP
    // ==========================================

    private fun setupUI() {
        setupRecyclerView()
        setupDragAndDrop()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        exercisesAdapter = WorkoutExercisesAdapter(
            onExerciseClick = { exercise ->
                if (!workoutDetailViewModel.isEditMode.value) {
                    showExerciseDetail(exercise)
                }
            },
            onAddSetClick = workoutDetailViewModel::addSetToExercise,
            onSetChanged = workoutDetailViewModel::updateExerciseSet,
            onDeleteExercise = { exercise ->
                showDeleteConfirmation(
                    "Elimina esercizio",
                    "Sei sicuro di voler eliminare \"${exercise.name}\"?"
                ) { workoutDetailViewModel.deleteExercise(exercise) }
            },
            onDeleteSet = workoutDetailViewModel::deleteExerciseSet,

            )

        exercisesRecyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupDragAndDrop() {
        // Crea il callback per il drag and drop
        dragCallback = ExerciseDragCallback { fromPosition, toPosition ->
            exercisesAdapter.notifyItemMoved(fromPosition, toPosition)
            workoutDetailViewModel.moveExercise(fromPosition, toPosition)
        }

        // Crea l'ItemTouchHelper
        itemTouchHelper = ItemTouchHelper(dragCallback)
        itemTouchHelper.attachToRecyclerView(exercisesRecyclerView)

        // Passa l'ItemTouchHelper all'adapter
        exercisesAdapter.setItemTouchHelper(itemTouchHelper)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { handleBackPress() }
        editButton.setOnClickListener { handleEditButtonClick() }
        startWorkoutButton.setOnClickListener { checkAndStartWorkout() }
        addExerciseBtn.setOnClickListener { showSelectExercisesDialog() }

        // Long click for editing workout card name
        workoutTitle.setOnLongClickListener {
            if (workoutDetailViewModel.isEditMode.value) {
                showEditWorkoutCardDialog()
                true
            } else false
        }
    }

    private fun handleEditButtonClick() {
        if (workoutDetailViewModel.isEditMode.value) {
            // Trying to exit edit mode - validate first
            val validationResult = workoutDetailViewModel.validateForExitEditMode()
            if (!validationResult.isValid) {
                // Show validation error message
                val errorMessage = "Inserisci i valori mancanti per peso e ripetizioni"
                showValidationError(errorMessage, validationResult.errors)
                return
            }
        }
        // Proceed with toggle if validation passed or entering edit mode
        workoutDetailViewModel.toggleEditMode()
    }

    private fun showValidationError(title: String, errors: List<String>) {
        val detailedMessage = if (errors.size == 1) {
            errors.first()
        } else {
            errors.joinToString("\n• ", "• ")
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(title)
            .setMessage(detailedMessage)
            .setPositiveButton("OK", null)
            .setIcon(R.drawable.ic_partial_complete)
            .show()

        // Also show a toast for quick feedback
        Toast.makeText(requireContext(), title, Toast.LENGTH_LONG).show()
    }

    private fun handleBackPress() {
        if (workoutDetailViewModel.isEditMode.value) {
            // Validate before leaving edit mode
            val validationResult = workoutDetailViewModel.validateForExitEditMode()
            if (!validationResult.isValid) {
                // Show validation error
                showValidationErrorWithBackPress(validationResult.errors)
            } else {
                // Ask user if they want to save changes before leaving edit mode
                showEditModeCancelConfirmation()
            }
        } else {
            val action = WorkoutDetailFragmentDirections.actionWorkoutDetailFragmentToHomeFragment()
            findNavController().navigate(action)
        }
    }

    private fun showValidationErrorWithBackPress(errors: List<String>) {
        val detailedMessage = if (errors.size == 1) {
            errors.first()
        } else {
            errors.joinToString("\n• ", "• ")
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Valori mancanti")
            .setMessage("Prima di uscire dalla modalità modifica:\n\n$detailedMessage")
            .setPositiveButton("Completa i valori", null)
            .setNegativeButton("Annulla modifiche") { _, _ ->
                // Force exit edit mode without validation
                workoutDetailViewModel.cancelEditMode()
                findNavController().popBackStack()
            }
            .setIcon(R.drawable.ic_partial_complete)
            .show()
    }

    private fun showEditModeCancelConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Uscire dalla modalità modifica?")
            .setMessage("Hai delle modifiche non salvate. Vuoi salvarle prima di uscire?")
            .setPositiveButton("Salva") { _, _ ->
                // Save changes and exit edit mode
                workoutDetailViewModel.toggleEditMode()
                findNavController().popBackStack()
            }
            .setNegativeButton("Annulla modifiche") { _, _ ->
                // Discard changes and exit edit mode
                workoutDetailViewModel.cancelEditMode()
                findNavController().popBackStack()
            }
            .setNeutralButton("Continua modifiche", null)
            .show()
    }

    // ==========================================
    // VIEWMODEL OBSERVATION WITH VALIDATION SUPPORT
    // ==========================================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine all observations into one block for efficiency
                launch { observeWorkoutCard() }
                launch { observeExercises() }
                launch { observeEditMode() }
                launch { observeUiState() }
            }
        }
    }

    private suspend fun observeWorkoutCard() {
        workoutDetailViewModel.workoutCard.collect { card ->
            card?.let { workoutTitle.text = it.name }
        }
    }

    private suspend fun observeExercises() {
        workoutDetailViewModel.exercisesWithSets.collect { exercises ->
            exercisesAdapter.submitList(exercises)
            updateEmptyState(exercises.isEmpty())
        }
    }

    private suspend fun observeEditMode() {
        workoutDetailViewModel.isEditMode.collect { isEditMode ->
            updateEditModeUI(isEditMode)
            exercisesAdapter.setEditMode(isEditMode)
            dragCallback.setDragEnabled(isEditMode)

            if (isEditMode) {
                showEditModeInstructions()
            }
        }
    }

    private suspend fun observeUiState() {
        workoutDetailViewModel.uiState.collect { state ->
            when (state) {
                is WorkoutDetailViewModel.UiState.Error -> showError(state.message)
                is WorkoutDetailViewModel.UiState.ValidationError -> {
                    // Handle validation errors from ViewModel
                    val errors = state.message.split("\n").filter { it.isNotBlank() }
                    if (errors.size > 1) {
                        // First line is the title, rest are details
                        showValidationError(errors.first(), errors.drop(1))
                    } else {
                        showValidationError("Inserisci i valori mancanti", errors)
                    }
                }
                else -> { /* Handle loading/success states if needed */ }
            }
        }
    }

    // ==========================================
    // UI UPDATES WITH DRAG SUPPORT
    // ==========================================

    private fun updateEditModeUI(isEditMode: Boolean) {
        editButton.text = if (isEditMode) "Fine" else "Modifica"
        addExerciseBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
        startWorkoutButton.visibility = if (isEditMode) View.GONE else View.VISIBLE

        // Update edit button style
        if (isEditMode) {
            editButton.setTextColor(resources.getColor(R.color.primary, null))
            editButton.text = "Fine"
        } else {
            editButton.setTextColor(resources.getColor(R.color.primary, null))
            editButton.text = "Modifica"
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateLayout.visibility = View.VISIBLE
            exercisesRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            exercisesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showEditModeInstructions() {
        // Mostra le istruzioni per il drag and drop solo la prima volta
        val prefs = requireContext().getSharedPreferences("app_prefs", 0)
        val hasShownInstructions = prefs.getBoolean("has_shown_drag_instructions", false)

        if (!hasShownInstructions) {
            Toast.makeText(
                requireContext(),
                "Usa l'icona a sinistra per trascinare e riordinare gli esercizi",
                Toast.LENGTH_LONG
            ).show()

            prefs.edit().putBoolean("has_shown_drag_instructions", true).apply()
        }
    }

    private fun showDeleteConfirmation(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Elimina") { _, _ -> onConfirm() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showSelectExercisesDialog() {
        val dialog = SelectExercisesDialog()
        dialog.setOnExercisesSelectedListener { selectedExercises ->
            workoutDetailViewModel.addMultipleExercises(selectedExercises)
            val message = if (selectedExercises.size == 1) {
                "Esercizio aggiunto"
            } else {
                "${selectedExercises.size} esercizi aggiunti"
            }
            showSuccess(message)
        }
        dialog.show(parentFragmentManager, "SelectExercisesDialog")
    }

    private fun showEditWorkoutCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_workout_card, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.workoutNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.workoutDescriptionInput)

        val currentCard = workoutDetailViewModel.workoutCard.value
        nameInput.setText(currentCard?.name ?: "")
        descriptionInput.setText(currentCard?.description ?: "")

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Modifica scheda")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim().takeIf { it.isNotEmpty() }

                if (name.isNotEmpty()) {
                    workoutDetailViewModel.updateWorkoutCard(name, description)
                    showSuccess("Scheda aggiornata")
                } else {
                    showError("Il nome è obbligatorio")
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ==========================================
    // WORKOUT MANAGEMENT
    // ==========================================

    private fun checkAndStartWorkout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val activeSession = workoutDetailViewModel.checkForActiveSession()
                if (activeSession != null) {
                    showActiveSessionDialog(activeSession)
                } else {
                    navigateToActiveWorkout()
                }
            } catch (e: Exception) {
                showError("Errore: ${e.message}")
            }
        }
    }

    private fun showActiveSessionDialog(activeSession: WorkoutSession) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Allenamento in corso")
            .setMessage("Hai già un allenamento in corso. Vuoi continuare quello esistente o annullarlo?")
            .setPositiveButton("Continua") { _, _ ->
                val action = WorkoutDetailFragmentDirections
                    .actionWorkoutDetailFragmentToActiveWorkoutFragment(activeSession.workoutCardId)
                findNavController().navigate(action)
            }
            .setNegativeButton("Annulla esistente") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    workoutDetailViewModel.cancelActiveSession(activeSession)
                    navigateToActiveWorkout()
                }
            }
            .setNeutralButton("Annulla", null)
            .show()
    }

    private fun navigateToActiveWorkout() {
        val cardId = workoutDetailViewModel.workoutCard.value?.id ?: return
        
        // Avvia monitoraggio battito cardiaco se abilitato
        if (heartRateManager.isConsentEnabled && heartRateManager.hasRequiredPermissions()) {
            Toast.makeText(requireContext(), "Monitoraggio battito cardiaco avviato", Toast.LENGTH_SHORT).show()
        }
        
        val action = WorkoutDetailFragmentDirections
            .actionWorkoutDetailFragmentToActiveWorkoutFragment(cardId)
        findNavController().navigate(action)
    }

    private fun showExerciseDetail(workoutExercise: WorkoutExercise) {
        val exerciseId = workoutExercise.exerciseId
        if (exerciseId != null) {
            val workoutCardId = workoutDetailViewModel.workoutCard.value?.id
            val action = WorkoutDetailFragmentDirections
                .actionWorkoutDetailFragmentToExerciseDetailFragment(exerciseId, workoutCardId)
            findNavController().navigate(action)
        } else {
            showError("Dettagli non disponibili per esercizi personalizzati")
        }
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}