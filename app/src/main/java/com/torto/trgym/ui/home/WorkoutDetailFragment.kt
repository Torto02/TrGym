package com.torto.trgym.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutExercisesAdapter
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.ui.dialogs.SelectExercisesDialog
import com.torto.trgym.ui.exercises.ExercisesFragmentDirections
import com.torto.trgym.viewmodel.home.WorkoutDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WorkoutDetailFragment : Fragment(R.layout.fragment_workout_detail) {

    private val workoutDetailViewModel: WorkoutDetailViewModel by viewModels()
    private lateinit var exercisesAdapter: WorkoutExercisesAdapter

    // Views
    private lateinit var backButton: TextView
    private lateinit var workoutTitle: TextView
    private lateinit var editButton: TextView
    private lateinit var exercisesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var startWorkoutButton: MaterialButton
    private lateinit var addExerciseBtn: FloatingActionButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModels()
    }

    private fun initViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        workoutTitle = view.findViewById(R.id.workoutTitle)
        editButton = view.findViewById(R.id.editButton)
        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        startWorkoutButton = view.findViewById(R.id.startWorkoutButton)
        addExerciseBtn = view.findViewById(R.id.addExerciseBtn)
    }

    private fun setupRecyclerView() {
        exercisesAdapter = WorkoutExercisesAdapter(
            onExerciseClick = { exercise ->
                if (!workoutDetailViewModel.isEditMode.value) {
                    showExerciseDetail(exercise)
                }
            },
            onAddSetClick = { exerciseId ->
                workoutDetailViewModel.addSetToExercise(exerciseId)
            },
            onSetChanged = { set ->
                workoutDetailViewModel.updateExerciseSet(set)
            },
            onDeleteExercise = { exercise ->
                showDeleteExerciseConfirmation(exercise)
            },
            onDeleteSet = { set ->
                workoutDetailViewModel.deleteExerciseSet(set)
            }
        )

        exercisesRecyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        editButton.setOnClickListener {
            workoutDetailViewModel.toggleEditMode()
        }

        startWorkoutButton.setOnClickListener {
            // TODO: Implementare avvio allenamento in futuro
            Toast.makeText(requireContext(), "Funzionalità in arrivo", Toast.LENGTH_SHORT).show()
        }

        addExerciseBtn.setOnClickListener {
            showSelectExercisesDialog()
        }

        // Long click sul titolo per modificare nome scheda
        workoutTitle.setOnLongClickListener {
            if (workoutDetailViewModel.isEditMode.value) {
                showEditWorkoutCardDialog()
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workoutDetailViewModel.workoutCard.collect { workoutCard ->
                    workoutCard?.let {
                        workoutTitle.text = it.name
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workoutDetailViewModel.exercisesWithSets.collect { exercises ->
                    exercisesAdapter.submitList(exercises)
                    updateEmptyState(exercises.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workoutDetailViewModel.isEditMode.collect { isEditMode ->
                    updateEditModeUI(isEditMode)
                    exercisesAdapter.setEditMode(isEditMode)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workoutDetailViewModel.uiState.collect { state ->
                    when (state) {
                        is WorkoutDetailViewModel.UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                        else -> { /* Handle other states if needed */ }
                    }
                }
            }
        }
    }

    private fun updateEditModeUI(isEditMode: Boolean) {
        editButton.text = if (isEditMode) "Fine" else "Modifica"
        addExerciseBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE

        // Update start button visibility and text
        startWorkoutButton.visibility = if (isEditMode) View.GONE else View.VISIBLE

        // Show save confirmation when exiting edit mode
        if (!isEditMode && ::exercisesAdapter.isInitialized) {
            Toast.makeText(requireContext(), "Modifiche salvate", Toast.LENGTH_SHORT).show()
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

    private fun showSelectExercisesDialog() {
        val dialog = SelectExercisesDialog()
        dialog.setOnExercisesSelectedListener { selectedExercises ->
            val exerciseNames = selectedExercises.map { it.title }
            workoutDetailViewModel.addMultipleExercises(exerciseNames)

            val message = if (selectedExercises.size == 1) {
                "Esercizio aggiunto"
            } else {
                "${selectedExercises.size} esercizi aggiunti"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "SelectExercisesDialog")
    }

    private fun showDeleteExerciseConfirmation(exercise: WorkoutExercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina esercizio")
            .setMessage("Sei sicuro di voler eliminare \"${exercise.name}\"? Tutti i set associati verranno eliminati.")
            .setPositiveButton("Elimina") { _, _ ->
                workoutDetailViewModel.deleteExercise(exercise)
                Toast.makeText(requireContext(), "Esercizio eliminato", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteSetConfirmation(set: ExerciseSet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina serie")
            .setMessage("Sei sicuro di voler eliminare la serie ${set.setNumber}?")
            .setPositiveButton("Elimina") { _, _ ->
                workoutDetailViewModel.deleteExerciseSet(set)
                Toast.makeText(requireContext(), "Serie eliminata", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showExerciseDetail(exercise: Int) {
        val action = ExercisesFragmentDirections
            .actionExercisesFragmentToExerciseDetailFragment(exercise)
        findNavController().navigate(action)

    }

//    private fun showExerciseDetailDialog(exercise: WorkoutExercise) {
//        MaterialAlertDialogBuilder(requireContext())
//            .setTitle(exercise.name)
//            .setMessage("Visualizza dettagli dell'esercizio, statistiche e cronologia.")
//            .setPositiveButton("Chiudi", null)
//            .setNeutralButton("Modifica") { _, _ ->
//                if (!workoutDetailViewModel.isEditMode.value) {
//                    workoutDetailViewModel.toggleEditMode()
//                }
//            }
//            .show()
//    }

    private fun showEditWorkoutCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_workout_card, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.workoutNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.workoutDescriptionInput)

        val currentCard = workoutDetailViewModel.workoutCard.value
        nameInput.setText(currentCard?.name ?: "")
        descriptionInput.setText(currentCard?.description ?: "")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica scheda")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim().takeIf { it.isNotEmpty() }

                if (name.isNotEmpty()) {
                    workoutDetailViewModel.updateWorkoutCard(name, description)
                    Toast.makeText(requireContext(), "Scheda aggiornata", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Il nome è obbligatorio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showQuickAddExerciseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_add_exercise, null)
        val exerciseNameInput = dialogView.findViewById<TextInputEditText>(R.id.exerciseNameInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Aggiungi esercizio")
            .setView(dialogView)
            .setPositiveButton("Aggiungi") { _, _ ->
                val exerciseName = exerciseNameInput.text.toString().trim()
                if (exerciseName.isNotEmpty()) {
                    workoutDetailViewModel.addExercise(exerciseName)
                    Toast.makeText(requireContext(), "Esercizio aggiunto", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Inserisci il nome dell'esercizio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::exercisesAdapter.isInitialized) {
            exercisesAdapter.cleanup()
        }
    }
}