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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutExercisesAdapter
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.ui.dialogs.SelectExercisesDialog
import com.torto.trgym.viewmodel.home.WorkoutDetailViewModel
import com.torto.trgym.viewmodel.home.ActiveWorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WorkoutDetailFragment : Fragment(R.layout.fragment_workout_detail) {

    private val workoutDetailViewModel: WorkoutDetailViewModel by viewModels()
    private val activeWorkoutViewModel: ActiveWorkoutViewModel by viewModels()
    private lateinit var exercisesAdapter: WorkoutExercisesAdapter

    // Views
    private lateinit var backButton: TextView
    private lateinit var workoutTitle: TextView
    private lateinit var editButton: TextView
    private lateinit var timerCard: MaterialCardView
    private lateinit var timerText: TextView
    private lateinit var pauseResumeButton: View
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
        timerCard = view.findViewById(R.id.timerCard)
        timerText = view.findViewById(R.id.timerText)
        pauseResumeButton = view.findViewById(R.id.pauseResumeButton)
        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        startWorkoutButton = view.findViewById(R.id.startWorkoutButton)
        addExerciseBtn = view.findViewById(R.id.addExerciseBtn)
    }

    private fun setupRecyclerView() {
        exercisesAdapter = WorkoutExercisesAdapter(
            onExerciseClick = { exercise ->
                // Naviga ai dettagli dell'esercizio
                // TODO: Implementare navigazione a ExerciseDetailFragment
            },
            onAddSetClick = { exerciseId ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    activeWorkoutViewModel.addSetToExercise(exerciseId)
                } else {
                    workoutDetailViewModel.addSetToExercise(exerciseId)
                }
            },
            onSetChanged = { set ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    // Durante allenamento, aggiorna WorkoutSessionSet
                    // TODO: Convertire ExerciseSet a WorkoutSessionSet
                } else {
                    workoutDetailViewModel.updateExerciseSet(set)
                }
            },
            onSetCompleted = { setId ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    activeWorkoutViewModel.completeSet(setId)
                }
            },
            onDeleteExercise = { exercise ->
                showDeleteExerciseConfirmation(exercise)
            },
            onDeleteSet = { set ->
                showDeleteSetConfirmation(set)
            },
            onSaveChanges = {
                // Callback chiamato quando si esce dalla modalità edit
                // Qui puoi aggiungere logica aggiuntiva se necessario
                // Ad esempio, mostrare un toast di conferma
                Toast.makeText(requireContext(), "Modifiche salvate", Toast.LENGTH_SHORT).show()
            }
        )

        exercisesRecyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }

        // Setup drag and drop per riordinare esercizi
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Solo se siamo in modalità edit
                if (activeWorkoutViewModel.isEditMode.value) {
                    exercisesAdapter.moveItem(fromPosition, toPosition)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return activeWorkoutViewModel.isEditMode.value
            }
        })

        itemTouchHelper.attachToRecyclerView(exercisesRecyclerView)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            // Se c'è un allenamento in corso, chiedi conferma
            if (activeWorkoutViewModel.isWorkoutActive.value) {
                showExitWorkoutConfirmation()
            } else {
                findNavController().popBackStack()
            }
        }

        editButton.setOnClickListener {
            activeWorkoutViewModel.toggleEditMode()
        }

        pauseResumeButton.setOnClickListener {
            // TODO: Implementare pausa/riprendi
        }

        startWorkoutButton.setOnClickListener {
            val workoutCard = workoutDetailViewModel.workoutCard.value
            if (workoutCard != null) {
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    // Concludi allenamento
                    showCompleteWorkoutConfirmation()
                } else {
                    // Inizia allenamento
                    activeWorkoutViewModel.startWorkout(workoutCard)
                }
            }
        }

        addExerciseBtn.setOnClickListener {
            showSelectExercisesDialog()
        }
    }

    private fun observeViewModels() {
        // Osserva WorkoutDetailViewModel
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
                    if (!activeWorkoutViewModel.isWorkoutActive.value) {
                        // Mostra gli esercizi della scheda solo se non c'è allenamento attivo
                        exercisesAdapter.submitList(exercises)
                        updateEmptyState(exercises.isEmpty())
                    }
                }
            }
        }

        // Osserva ActiveWorkoutViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activeWorkoutViewModel.isWorkoutActive.collect { isActive ->
                    updateUIForWorkoutState(isActive)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activeWorkoutViewModel.exercisesWithSets.collect { sessionExercises ->
                    if (activeWorkoutViewModel.isWorkoutActive.value) {
                        // Durante allenamento, mostra gli esercizi della sessione
                        // TODO: Convertire WorkoutSessionExerciseDetail a formato adapter
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activeWorkoutViewModel.elapsedTime.collect { elapsedTime ->
                    timerText.text = formatElapsedTime(elapsedTime)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activeWorkoutViewModel.isEditMode.collect { isEditMode ->
                    editButton.text = if (isEditMode) "Fine" else "Modifica"
                    exercisesAdapter.setEditMode(isEditMode)
                    addExerciseBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activeWorkoutViewModel.uiState.collect { state ->
                    when (state) {
                        is ActiveWorkoutViewModel.UiState.Loading -> {
                            // Mostra loading
                        }
                        is ActiveWorkoutViewModel.UiState.WorkoutCompleted -> {
                            showWorkoutCompletedDialog(state.session)
                        }
                        is ActiveWorkoutViewModel.UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun updateUIForWorkoutState(isWorkoutActive: Boolean) {
        if (isWorkoutActive) {
            // Modalità allenamento attivo
            timerCard.visibility = View.VISIBLE
            startWorkoutButton.text = "Concludi Allenamento"
            startWorkoutButton.setBackgroundColor(resources.getColor(R.color.gray, null))
            editButton.visibility = View.VISIBLE
        } else {
            // Modalità preparazione
            timerCard.visibility = View.GONE
            startWorkoutButton.text = "Inizia Allenamento"
            startWorkoutButton.setBackgroundColor(resources.getColor(R.color.primary, null))
            editButton.visibility = View.VISIBLE
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

    private fun formatElapsedTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60)) % 24

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun showSelectExercisesDialog() {
        val dialog = SelectExercisesDialog()
        dialog.setOnExercisesSelectedListener { selectedExercises ->
            val exerciseNames = selectedExercises.map { it.title }

            if (activeWorkoutViewModel.isWorkoutActive.value) {
                // TODO: Aggiungi esercizi alla sessione attiva
            } else {
                workoutDetailViewModel.addMultipleExercises(exerciseNames)
            }

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
            .setMessage("Sei sicuro di voler eliminare \"${exercise.name}\"?")
            .setPositiveButton("Elimina") { _, _ ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    // TODO: Elimina dalla sessione attiva
                } else {
                    workoutDetailViewModel.deleteExercise(exercise)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteSetConfirmation(set: ExerciseSet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina serie")
            .setMessage("Sei sicuro di voler eliminare la serie ${set.setNumber}?")
            .setPositiveButton("Elimina") { _, _ ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    // TODO: Elimina dalla sessione attiva
                } else {
                    workoutDetailViewModel.deleteExerciseSet(set)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showExitWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Allenamento in corso")
            .setMessage("Hai un allenamento in corso. Vuoi davvero uscire? L'allenamento continuerà in background.")
            .setPositiveButton("Esci") { _, _ ->
                findNavController().popBackStack()
            }
            .setNegativeButton("Rimani", null)
            .show()
    }

    private fun showCompleteWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Concludi allenamento")
            .setMessage("Sei sicuro di voler concludere l'allenamento?")
            .setPositiveButton("Concludi") { _, _ ->
                activeWorkoutViewModel.completeWorkout()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showWorkoutCompletedDialog(session: com.torto.trgym.database.entities.WorkoutSession) {
        val duration = formatElapsedTime(session.totalDurationMs)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Allenamento completato!")
            .setMessage("Durata: $duration\n\nBravo! Hai completato il tuo allenamento.")
            .setPositiveButton("OK") { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::exercisesAdapter.isInitialized) {
            exercisesAdapter.cleanup()
        }
    }
}