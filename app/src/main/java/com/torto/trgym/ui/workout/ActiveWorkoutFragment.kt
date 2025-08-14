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
import com.torto.trgym.R
import com.torto.trgym.adapters.ActiveWorkoutExercisesAdapter
import com.torto.trgym.services.WorkoutTimerService
import com.torto.trgym.viewmodel.workout.ActiveWorkoutViewModel
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(
                timerUpdateReceiver,
                IntentFilter(WorkoutTimerService.ACTION_TIME_UPDATE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            // For older Android versions, use the previous method
            requireActivity().registerReceiver(
                timerUpdateReceiver,
                IntentFilter(WorkoutTimerService.ACTION_TIME_UPDATE)
            )
        }
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
    }
    
    private fun setupRecyclerView() {
        exercisesAdapter = ActiveWorkoutExercisesAdapter(
            onSetCompletionChanged = { setId, isCompleted ->
                viewModel.toggleSetCompletion(setId, isCompleted)
            },
            onSetValueChanged = { set ->
                viewModel.updateSessionSet(set)
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
                            findNavController().popBackStack()
                        }
                        is ActiveWorkoutViewModel.UiState.WorkoutCancelled -> {
                            Toast.makeText(requireContext(), "Allenamento annullato", Toast.LENGTH_SHORT).show()
                            stopTimerService()
                            findNavController().popBackStack()
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
                findNavController().popBackStack()
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
}
