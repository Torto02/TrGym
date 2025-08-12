package com.torto.trgym.ui.home


import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.util.Log
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
import android.widget.ImageView
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutSessionExercise
import com.torto.trgym.database.entities.WorkoutSessionSet
import com.torto.trgym.database.entities.WorkoutCard
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
    
    private var hasShownActiveWorkoutWarning = false

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
                // Note: ExerciseDetailFragment expects exerciseId as Int, but we have String
                // This navigation might need to be adjusted based on actual ExerciseDetailFragment implementation
                try {
                    findNavController().navigate(R.id.action_workoutDetailFragment_to_exerciseDetailFragment)
                } catch (e: Exception) {
                    Log.w("WorkoutDetailFragment", "Navigation to exercise detail failed", e)
                }
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
                    // Durante allenamento, aggiorna il WorkoutSessionSet corrispondente
                    updateSessionSetFromExerciseSet(set)
                } else {
                    workoutDetailViewModel.updateExerciseSet(set)
                }
            },
            onSetCompleted = { setId ->
                if (activeWorkoutViewModel.isWorkoutActive.value) {
                    activeWorkoutViewModel.completeSet(setId)
                    //! Non voglio che parta in automatico il timer per il set
                    //! Per ora, non avvio il timer automatico per il set completato
                    val exerciseId = getCurrentExerciseIdForSet(setId)
                    exerciseId?.let {
                        activeWorkoutViewModel.startRestTimerForSet(it)
                    }
                }
            },
            onDeleteExercise = { exercise ->
                showDeleteExerciseConfirmation(exercise)
            },
            onDeleteSet = { set ->
                showDeleteSetConfirmation(set)
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
//            if (activeWorkoutViewModel.isWorkoutActive.value) {
//                showExitWorkoutConfirmation()
//            } else {
                findNavController().popBackStack()
//            }
        }

        editButton.setOnClickListener {
            activeWorkoutViewModel.toggleEditMode()
        }

        pauseResumeButton.setOnClickListener {
            if (activeWorkoutViewModel.isWorkoutActive.value) {
                val timer = activeWorkoutViewModel.workoutTimer.value
                if (timer.isPaused()) {
                    activeWorkoutViewModel.resumeWorkout()
                    pauseResumeButton.findViewById<ImageView>(R.id.pauseResumeButton)
                        ?.setImageResource(R.drawable.ic_pause)
                } else {
                    activeWorkoutViewModel.pauseWorkout()
                    pauseResumeButton.findViewById<ImageView>(R.id.pauseResumeButton)
                        ?.setImageResource(R.drawable.ic_play)
                }
            }
        }

        startWorkoutButton.setOnClickListener {
            val workoutCard = workoutDetailViewModel.workoutCard.value
            val currentSession = activeWorkoutViewModel.currentSession.value
            val isActiveWorkout = activeWorkoutViewModel.isWorkoutActive.value && 
                                  currentSession?.workoutCardId == workoutCard?.id
            
            if (workoutCard != null) {
                if (isActiveWorkout) {
                    // Concludi allenamento
                    showCompleteWorkoutConfirmation()
                } else if (activeWorkoutViewModel.isWorkoutActive.value) {
                    // There's a different active workout
                    showStartWorkoutWithActiveWarning(workoutCard, currentSession?.name ?: "")
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
//                    if (!activeWorkoutViewModel.isWorkoutActive.value) {
                        // Mostra gli esercizi della scheda solo se non c'è allenamento attivo
                        exercisesAdapter.submitList(exercises)
                        updateEmptyState(exercises.isEmpty())
                    }
//                }
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
                        // Check if the active workout matches the current card
                        val currentSession = activeWorkoutViewModel.currentSession.value
                        val requestedCardId = workoutDetailViewModel.workoutCard.value?.id
                        
                        if (currentSession?.workoutCardId == requestedCardId) {
                            // Show active workout data
                            val convertedExercises = convertSessionExercisesToDisplayFormat(sessionExercises)
                            exercisesAdapter.submitList(convertedExercises)
                            exercisesAdapter.setWorkoutActive(true)
                            updateEmptyState(sessionExercises.isEmpty())
                        } else {
                            // User is viewing a different card while workout is active
                            // Show warning once and keep regular card data visible
                            if (!hasShownActiveWorkoutWarning) {
                                hasShownActiveWorkoutWarning = true
                                showActiveWorkoutWarning(currentSession?.name ?: "")
                            }
                        }
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
            startWorkoutButton.setBackgroundColor(resources.getColor(R.color.primary, null))
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
                // Aggiungi esercizi alla sessione attiva
                val sessionId = activeWorkoutViewModel.currentSession.value?.id
                if (sessionId != null) {
                    exerciseNames.forEach { exerciseName ->
                        activeWorkoutViewModel.addExerciseToSession(exerciseName)
                    }
                }
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
                    // Trova l'esercizio della sessione corrispondente
                    val sessionExercise = findSessionExerciseByOriginalId(exercise.id)
                    sessionExercise?.let {
                        activeWorkoutViewModel.deleteSessionExercise(it)
                    }
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

    // ========== HELPER METHODS ==========

    private fun updateSessionSetFromExerciseSet(exerciseSet: ExerciseSet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Find the corresponding WorkoutSessionSet
                val sessionExercises = activeWorkoutViewModel.exercisesWithSets.value
                val sessionSet = sessionExercises
                    .flatMap { it.sets }
                    .find { it.id == exerciseSet.id }
                
                sessionSet?.let { originalSessionSet ->
                    val updatedSessionSet = originalSessionSet.copy(
                        actualWeightKg = exerciseSet.weightKg,
                        actualReps = exerciseSet.reps,
                        updatedAt = System.currentTimeMillis()
                    )
                    activeWorkoutViewModel.updateSet(updatedSessionSet)
                }
            } catch (e: Exception) {
                Log.e("WorkoutDetailFragment", "Error updating session set", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Forza il salvataggio di tutti i campi che potrebbero avere il focus
        // quando l'utente lascia il fragment
        view?.clearFocus()
    }

    override fun onStop() {
        super.onStop()
        // Forza il salvataggio anche quando il fragment viene fermato
        view?.clearFocus()
        exercisesAdapter.flushAllPendingUpdates()
    }

    private fun getCurrentExerciseIdForSet(setId: String): String? {
        // Cerca nei dati attuali degli esercizi per trovare l'exerciseId di questo set
        val sessionExercises = activeWorkoutViewModel.exercisesWithSets.value
        return sessionExercises.find { exerciseDetail ->
            exerciseDetail.sets.any { it.id == setId }
        }?.exercise?.id
    }

    private fun convertSessionExercisesToDisplayFormat(
        sessionExercises: List<WorkoutSessionExerciseDetail>
    ): List<com.torto.trgym.database.dto.WorkoutExerciseDetail> {
        return sessionExercises.map { sessionExerciseDetail ->
            val workoutExercise = com.torto.trgym.database.entities.WorkoutExercise(
                id = sessionExerciseDetail.exercise.id,
                workoutCardId = "", // Non utilizzato per display
                name = sessionExerciseDetail.exercise.name,
                orderIndex = sessionExerciseDetail.exercise.orderIndex,
                updatedAt = sessionExerciseDetail.exercise.updatedAt
            )

            val exerciseSets = sessionExerciseDetail.sets.map { sessionSet ->
                com.torto.trgym.database.entities.ExerciseSet(
                    id = sessionSet.id,
                    exerciseId = sessionSet.exerciseId,
                    setNumber = sessionSet.setNumber,
                    weightKg = sessionSet.actualWeightKg ?: sessionSet.targetWeightKg,
                    reps = sessionSet.actualReps ?: sessionSet.targetReps,
                    createdAt = sessionSet.createdAt,
                    updatedAt = sessionSet.updatedAt
                )
            }

            // Update adapter with completion status for each set
            sessionExerciseDetail.sets.forEach { sessionSet ->
//                exercisesAdapter.updateSessionSetCompletion(
//                    sessionExerciseDetail.exercise.id,
//                    sessionSet.id,
//                    sessionSet.isCompleted
//                )
            }

            com.torto.trgym.database.dto.WorkoutExerciseDetail(
                exercise = workoutExercise,
                sets = exerciseSets
            )
        }
    }

    private fun findSessionExerciseByOriginalId(originalId: String): WorkoutSessionExercise? {
        return activeWorkoutViewModel.exercisesWithSets.value
            .find { it.exercise.originalExerciseId == originalId }?.exercise
    }

    private fun findSessionSetByOriginalId(originalId: String): WorkoutSessionSet? {
        return activeWorkoutViewModel.exercisesWithSets.value
            .flatMap { it.sets }
            .find { it.id == originalId } // Per i set, l'ID rimane lo stesso
    }

    private fun showWorkoutPreview() {
        val exercises = workoutDetailViewModel.exercisesWithSets.value
        val totalExercises = exercises.size
        val totalSets = exercises.sumOf { it.sets.size }
        val estimatedTime = totalSets * 3 // 3 minuti per set (stima)

        val message = buildString {
            append("Riepilogo allenamento:\n\n")
            append("• $totalExercises esercizi\n")
            append("• $totalSets serie totali\n")
            append("• Tempo stimato: ${estimatedTime} minuti\n\n")

            exercises.forEach { exerciseDetail ->
                append("${exerciseDetail.exercise.name}: ${exerciseDetail.sets.size} serie\n")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Anteprima Allenamento")
            .setMessage(message)
            .setPositiveButton("Inizia") { _, _ ->
                val workoutCard = workoutDetailViewModel.workoutCard.value
                workoutCard?.let { activeWorkoutViewModel.startWorkout(it) }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showActiveWorkoutWarning(activeWorkoutName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Allenamento in corso")
            .setMessage("Hai un allenamento attivo: \"$activeWorkoutName\". Vuoi continuare l'allenamento attivo o visualizzare questa scheda?")
            .setPositiveButton("Continua allenamento") { _, _ ->
                // Navigate back and then to the active workout
                val currentSession = activeWorkoutViewModel.currentSession.value
                currentSession?.let { session ->
                    findNavController().popBackStack()
                    val action = HomeFragmentDirections.actionHomeToWorkoutDetail(session.workoutCardId)
                    findNavController().navigate(action)
                }
            }
            .setNegativeButton("Visualizza scheda", null)
            .show()
    }

    private fun showStartWorkoutWithActiveWarning(workoutCard: WorkoutCard, activeWorkoutName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Allenamento in corso")
            .setMessage("Hai già un allenamento attivo: \"$activeWorkoutName\". Vuoi terminarlo e iniziare \"${workoutCard.name}\"?")
            .setPositiveButton("Termina e inizia nuovo") { _, _ ->
                // Complete current workout and start new one
                activeWorkoutViewModel.completeWorkout()
                activeWorkoutViewModel.startWorkout(workoutCard)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}