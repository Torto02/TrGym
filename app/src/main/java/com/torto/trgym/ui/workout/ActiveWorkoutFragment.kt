package com.torto.trgym.ui.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.torto.trgym.R
import com.torto.trgym.adapters.ActiveWorkoutExercisesAdapter
import com.torto.trgym.service.HeartRateDataListenerService
import com.torto.trgym.viewmodel.workout.ActiveWorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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
    private lateinit var pauseResumeButton: ImageView
    
    // Heart Rate Views
    private lateinit var heartRateCard: MaterialCardView
    private lateinit var heartRateValue: TextView
    private lateinit var heartRateStatus: TextView
    
    // Heart Rate Data Collection
    private val heartRateReadings = mutableListOf<Int>()
    
    // Heart Rate BroadcastReceiver
    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED) {
                val heartRate = intent.getIntExtra(HeartRateDataListenerService.EXTRA_HEART_RATE, -1)
                if (heartRate > 0) {
                    // Add heart rate to collection for averaging
                    heartRateReadings.add(heartRate)
                    updateHeartRateUI(heartRate)
                    Log.d("ActiveWorkout", "Heart rate received: $heartRate, Total readings: ${heartRateReadings.size}")
                }
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
        setupHeartRateMonitoring()
    }

    private fun initViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        workoutTitle = view.findViewById(R.id.workoutTitle)
        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        completeWorkoutButton = view.findViewById(R.id.completeWorkoutButton)
        cancelWorkoutButton = view.findViewById(R.id.cancelWorkoutButton)
        
        // Heart Rate Views
        heartRateCard = view.findViewById(R.id.heartRateCard)
        heartRateValue = view.findViewById(R.id.heartRateValue)
        heartRateStatus = view.findViewById(R.id.heartRateStatus)
    }

    private fun setupRecyclerView() {
        exercisesAdapter = ActiveWorkoutExercisesAdapter(
            onSetCompletionChanged = { setId, isCompleted ->
                viewModel.toggleSetCompletion(setId, isCompleted)
            },
            onSetValueChangedSimple = { setId, weight, reps ->
                viewModel.updateSessionSetFromValues(setId, weight, reps)
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
                            findNavController().navigate(ActiveWorkoutFragmentDirections.actionActiveWorkoutFragmentToHomeFragment())
                        }
                        is ActiveWorkoutViewModel.UiState.WorkoutCancelled -> {
                            Toast.makeText(requireContext(), "Allenamento annullato", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(ActiveWorkoutFragmentDirections.actionActiveWorkoutFragmentToHomeFragment())
                        }
                        else -> { /* Handle other states if needed */ }
                    }
                }
            }
        }

        // Check current state immediately in case we missed the ReadyToStart transition
        viewLifecycleOwner.lifecycleScope.launch {
            val currentState = viewModel.uiState.value
            if (currentState is ActiveWorkoutViewModel.UiState.ReadyToStart) {
                startWorkout()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedSets.collect { completedSets ->
                    // Don't update yet - wait for both completedSets and setIdMapping
                    // The update will happen in the combined collector below
                }
            }
        }

        // Add a new collector for the setIdMapping
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect both completedSets and setIdMapping together
                viewModel.completedSets.combine(viewModel.setIdMapping) { completedSets, setIdMapping ->
                    Pair(completedSets, setIdMapping)
                }.collect { (completedSets, setIdMapping) ->
                    // First update the ID mapping
                    exercisesAdapter.updateSetIdMapping(setIdMapping)
                    // Then update the completed sets
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
    }

    private fun showCompleteWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Completa allenamento")
            .setMessage("Sei sicuro di voler concludere l'allenamento? Verranno salvati tutti i set completati.")
            .setPositiveButton("Completa") { _, _ ->
                val averageHeartRate = calculateAverageHeartRate()
                viewModel.completeWorkout(averageHeartRate)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showCancelWorkoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Annulla allenamento")
            .setMessage("Sei sicuro di voler annullare l'allenamento? Tutti i progressi andranno persi.")
            .setPositiveButton("Annulla allenamento") { _, _ ->
                viewModel.cancelWorkout()
            }
            .setNegativeButton("Continua allenamento", null)
            .show()
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Uscire dall'allenamento?")
            .setMessage("L'allenamento rimarrÃ  attivo. Per completarlo o annullarlo torna a questa schermata.")
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
    
    // ==========================================
    // HEART RATE MONITORING METHODS
    // ==========================================
    
    private fun setupHeartRateMonitoring() {
        checkWearOSConnection()
        
        // Register BroadcastReceiver
        val filter = IntentFilter(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(heartRateReceiver, filter)
    }
    
    private fun checkWearOSConnection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(requireContext())
                val nodes: Collection<Node> = nodeClient.connectedNodes.await()
                
                if (nodes.isNotEmpty()) {
                    val connectedDevice = nodes.first()
                    Log.d("ActiveWorkout", "Wear OS connected: ${connectedDevice.displayName}")
                    
                    // Show heart rate card and update status
                    heartRateCard.visibility = View.VISIBLE
                    heartRateStatus.text = "Connesso: ${connectedDevice.displayName}"
                    heartRateValue.text = "In attesa..."
                } else {
                    Log.d("ActiveWorkout", "No Wear OS devices connected")
                    heartRateCard.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("ActiveWorkout", "Error checking Wear OS connection", e)
                heartRateCard.visibility = View.GONE
            }
        }
    }
    
    private fun updateHeartRateUI(heartRate: Int) {
        heartRateValue.text = "$heartRate BPM"
        heartRateStatus.text = "Aggiornato ora"
        
        // Update timestamp for status
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        heartRateStatus.text = "Aggiornato: $currentTime"
    }
    
    private fun calculateAverageHeartRate(): Int? {
        return if (heartRateReadings.isNotEmpty()) {
            val average = heartRateReadings.average().toInt()
            Log.d("ActiveWorkout", "Calculated average heart rate: $average from ${heartRateReadings.size} readings")
            average
        } else {
            Log.d("ActiveWorkout", "No heart rate readings collected during workout")
            null
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-register receiver in case it was unregistered
        val filter = IntentFilter(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(heartRateReceiver, filter)
        
        // Check connection again when fragment resumes
        checkWearOSConnection()
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(heartRateReceiver)
    }
}