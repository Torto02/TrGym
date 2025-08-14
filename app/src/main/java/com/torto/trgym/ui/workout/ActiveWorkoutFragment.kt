package com.torto.trgym.ui.workout

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.torto.trgym.R
import com.torto.trgym.adapters.ActiveWorkoutExercisesAdapter
import com.torto.trgym.services.WorkoutTimerService
import com.torto.trgym.ui.dialogs.SelectExercisesDialog
import com.torto.trgym.viewmodel.workout.ActiveWorkoutViewModel
import com.torto.trgym.database.entities.WorkoutSessionExercise
import com.torto.trgym.database.entities.WorkoutSessionSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ActiveWorkoutFragment : Fragment(R.layout.fragment_active_workout) {

    private val viewModel: ActiveWorkoutViewModel by viewModels()
    private lateinit var exercisesAdapter: ActiveWorkoutExercisesAdapter

    // Views
    private lateinit var backButton: TextView
    private lateinit var workoutTitle: TextView
    private lateinit var exercisesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var completeWorkoutButton: MaterialButton
    private lateinit var cancelWorkoutButton: MaterialButton
    private lateinit var timerCard: MaterialCardView
    private lateinit var timerText: TextView
    private lateinit var pauseResumeButton: ImageView
    private lateinit var editButton: TextView
    private lateinit var addExerciseBtn: FloatingActionButton // Aggiungo il FAB

    // Timer service
    private var timerService: WorkoutTimerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutTimerService.LocalBinder
            timerService = binder.getService()
            isBound = true

            // Update initial state
            updateTimerDisplay(timerService?.getElapsedTime() ?: 0L)
            updatePauseResumeButton(timerService?.isTimerRunning() ?: false)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    private val timerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WorkoutTimerService.ACTION_TIME_UPDATE) {
                val elapsedTime = intent.getLongExtra(WorkoutTimerService.EXTRA_ELAPSED_TIME, 0L)
                val isRunning = intent.getBooleanExtra(WorkoutTimerService.EXTRA_IS_RUNNING, false)

                viewModel.updateElapsedTime(elapsedTime)
                updateTimerDisplay(elapsedTime)
                updatePauseResumeButton(isRunning)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        setupBackPressHandler()

        // Register timer updates receiver with the NOT_EXPORTED flag for Android 13+
        requireActivity().registerReceiver(
            timerUpdateReceiver,
            IntentFilter(WorkoutTimerService.ACTION_TIME_UPDATE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        workoutTitle = view.findViewById(R.id.workoutTitle)
        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        completeWorkoutButton = view.findViewById(R.id.completeWorkoutButton)
        cancelWorkoutButton = view.findViewById(R.id.cancelWorkoutButton)
        timerCard = view.findViewById(R.id.timerCard)
        timerText = view.findViewById(R.id.timerText)
        pauseResumeButton = view.findViewById(R.id.pauseResumeButton)
        editButton = view.findViewById(R.id.editButton)
        addExerciseBtn = view.findViewById(R.id.addExerciseBtn) // Inizializzo il FAB
    }

    private fun setupRecyclerView() {
        exercisesAdapter = ActiveWorkoutExercisesAdapter(
            onSetCompletionChanged = { setId, isCompleted ->
                viewModel.toggleSetCompletion(setId, isCompleted)
            },
            onSetValueChanged = { set ->
                viewModel.updateSessionSetValues(set)
            },
            onSetValueChangedSimple = { setId, weight, reps ->
                viewModel.updateSessionSetFromValues(setId, weight, reps)
            },
            onExerciseMenuClicked = { exerciseId ->
                showExerciseMenu(exerciseId)
            },
            onSetMenuClicked = { setId ->
                showSetMenu(setId)
            },
            onAddSetClicked = { exerciseId ->
                viewModel.addSetToSessionExercise(exerciseId)
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
            showExitConfirmationDialog()
        }

        completeWorkoutButton.setOnClickListener {
            showCompleteWorkoutConfirmation()
        }

        cancelWorkoutButton.setOnClickListener {
            showCancelWorkoutConfirmation()
        }

        pauseResumeButton.setOnClickListener {
            toggleTimerState()
        }

        // Aggiungo il listener per il pulsante di modifica
        editButton.setOnClickListener {
            viewModel.toggleEditMode()
        }

        // Listener per il pulsante FAB di aggiunta esercizi
        addExerciseBtn.setOnClickListener {
            showSelectExercisesDialog()
        }
    }

    // Metodo per mostrare il dialog di selezione esercizi
    private fun showSelectExercisesDialog() {
        val dialog = SelectExercisesDialog()
        dialog.setOnExercisesSelectedListener { selectedExercises ->
            viewModel.addExercisesToSession(selectedExercises)
            val message = if (selectedExercises.size == 1) {
                "Esercizio aggiunto all'allenamento"
            } else {
                "${selectedExercises.size} esercizi aggiunti all'allenamento"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "SelectExercisesDialog")
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.workoutCard.collect { workoutCard ->
                    workoutCard?.let {
                        workoutTitle.text = it.name
                    }
                }
            }
        }

        // Osservo lo stato di edit mode
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEditMode.collect { isEditMode ->
                    updateEditModeUI(isEditMode)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exercisesWithSets.collect { exercises ->
                    exercisesAdapter.submitList(exercises)
                    updateEmptyState(exercises.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ActiveWorkoutViewModel.UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                        is ActiveWorkoutViewModel.UiState.ReadyToStart -> {
                            startWorkout()
                        }
                        is ActiveWorkoutViewModel.UiState.WorkoutCompleted -> {
                            Toast.makeText(requireContext(), "Allenamento completato!", Toast.LENGTH_SHORT).show()
                            stopTimerService()
                            findNavController().navigate(ActiveWorkoutFragmentDirections.actionActiveWorkoutFragmentToHomeFragment())
                        }
                        is ActiveWorkoutViewModel.UiState.WorkoutCancelled -> {
                            Toast.makeText(requireContext(), "Allenamento annullato", Toast.LENGTH_SHORT).show()
                            stopTimerService()
                            findNavController().navigate(ActiveWorkoutFragmentDirections.actionActiveWorkoutFragmentToHomeFragment())
                        }
                        else -> { /* Handle other states if needed */ }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.timerRunning.collect { isRunning ->
                    updatePauseResumeButton(isRunning)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedSets.collect { completedSets ->
                    exercisesAdapter.updateCompletedSets(completedSets)
                }
            }
        }
    }

    // Aggiungo il metodo per aggiornare l'UI in base allo stato di edit mode
    private fun updateEditModeUI(isEditMode: Boolean) {
        editButton.text = if (isEditMode) "Fine" else "Modifica"
        
        // Gestisco la visibilità del FAB
        addExerciseBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
        
        // Passo lo stato di edit mode all'adapter
        exercisesAdapter.setEditMode(isEditMode)
        
        // Nascondi/mostra i pulsanti di azione inferiori durante la modifica
        completeWorkoutButton.visibility = if (isEditMode) View.GONE else View.VISIBLE
        cancelWorkoutButton.visibility = if (isEditMode) View.GONE else View.VISIBLE
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

    private fun startWorkout() {
        viewModel.startWorkout()
        startTimerService()
        timerCard.visibility = View.VISIBLE
    }

    private fun startTimerService() {
        val serviceIntent = Intent(requireContext(), WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra("sessionId", viewModel.activeSession.value?.id)
        }

        requireContext().startService(serviceIntent)
        requireContext().bindService(
            Intent(requireContext(), WorkoutTimerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun stopTimerService() {
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }

        requireContext().startService(
            Intent(requireContext(), WorkoutTimerService::class.java).apply {
                action = WorkoutTimerService.ACTION_STOP
            }
        )
    }

    private fun toggleTimerState() {
        if (timerService?.isTimerRunning() == true) {
            pauseTimer()
        } else {
            resumeTimer()
        }
    }

    private fun pauseTimer() {
        requireContext().startService(
            Intent(requireContext(), WorkoutTimerService::class.java).apply {
                action = WorkoutTimerService.ACTION_PAUSE
            }
        )
        viewModel.pauseTimer()
    }

    private fun resumeTimer() {
        requireContext().startService(
            Intent(requireContext(), WorkoutTimerService::class.java).apply {
                action = WorkoutTimerService.ACTION_RESUME
            }
        )
        viewModel.resumeTimer()
    }

    private fun updateTimerDisplay(elapsedMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60

        timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updatePauseResumeButton(isRunning: Boolean) {
        pauseResumeButton.setImageResource(
            if (isRunning) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun showCompleteWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Completa allenamento")
            .setMessage("Sei sicuro di voler concludere l'allenamento? Verranno salvati tutti i set completati.")
            .setPositiveButton("Completa") { _, _ ->
                viewModel.completeWorkout()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showCancelWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Annulla allenamento")
            .setMessage("Sei sicuro di voler annullare l'allenamento? Tutti i progressi andranno persi.")
            .setPositiveButton("Annulla allenamento") { _, _ ->
                viewModel.cancelWorkout()
            }
            .setNegativeButton("Continua allenamento", null)
            .show()
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Uscire dall'allenamento?")
            .setMessage("L'allenamento rimarrà attivo. Per completarlo o annullarlo torna a questa schermata.")
            .setPositiveButton("Esci") { _, _ ->
                findNavController().navigate(ActiveWorkoutFragmentDirections.actionActiveWorkoutFragmentToHomeFragment())
            }
            .setNegativeButton("Resta", null)
            .show()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showExitConfirmationDialog()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireActivity().unregisterReceiver(timerUpdateReceiver)
        } catch (e: Exception) {
            // Ignore if receiver wasn't registered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
    }

    private fun showExerciseMenu(exerciseId: String) {
        // Find the session exercise from ViewModel
        val sessionExercise = viewModel.sessionExercises.value.find { it.id == exerciseId }
        if (sessionExercise != null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Opzioni esercizio")
                .setItems(arrayOf("Elimina esercizio")) { _, which ->
                    when (which) {
                        0 -> showDeleteExerciseConfirmation(sessionExercise)
                    }
                }
                .show()
        }
    }

    private fun showSetMenu(setId: String) {
        // Find the session set from ViewModel
        val sessionSet = viewModel.sessionSets.value.values.flatten().find { it.id == setId }
        if (sessionSet != null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Opzioni serie")
                .setItems(arrayOf("Elimina serie")) { _, which ->
                    when (which) {
                        0 -> showDeleteSetConfirmation(sessionSet)
                    }
                }
                .show()
        }
    }

    private fun showDeleteExerciseConfirmation(sessionExercise: WorkoutSessionExercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina esercizio")
            .setMessage("Sei sicuro di voler eliminare \"${sessionExercise.name}\" e tutte le sue serie?")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteSessionExercise(sessionExercise)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteSetConfirmation(sessionSet: WorkoutSessionSet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina serie")
            .setMessage("Sei sicuro di voler eliminare la serie ${sessionSet.setNumber}?")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteSessionSet(sessionSet)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
