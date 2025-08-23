package com.torto.trgym.ui.workout

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutSessionExerciseAdapter
import com.torto.trgym.viewmodel.workout.WorkoutSessionDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class WorkoutSessionDetailFragment : Fragment(R.layout.fragment_workout_session_detail) {

    private val args: WorkoutSessionDetailFragmentArgs by navArgs()
    private val viewModel: WorkoutSessionDetailViewModel by viewModels()
    private lateinit var exercisesAdapter: WorkoutSessionExerciseAdapter

    // Views
    private val backButton by lazy { requireView().findViewById<TextView>(R.id.backButton) }
    private val workoutTitle by lazy { requireView().findViewById<TextView>(R.id.workoutTitle) }
    private val workoutDate by lazy { requireView().findViewById<TextView>(R.id.workoutDate) }
    private val workoutDuration by lazy { requireView().findViewById<TextView>(R.id.workoutDuration) }
    private val burnedCalories by lazy { requireView().findViewById<TextView>(R.id.burnedCalories) }
    private val exercisesRecyclerView by lazy { requireView().findViewById<RecyclerView>(R.id.exercisesRecyclerView) }
    private val emptyStateLayout by lazy { requireView().findViewById<View>(R.id.emptyStateLayout) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            val action = WorkoutSessionDetailFragmentDirections
                .actionWorkoutSessionDetailFragmentToWorkoutHistoryFragment()
            findNavController().navigate(action)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        viewModel.loadWorkoutSession(args.sessionId)
    }

    private fun setupUI() {
        setupRecyclerView()
        setupClickListeners()
    }


    private fun setupRecyclerView() {
        exercisesAdapter = WorkoutSessionExerciseAdapter()

        exercisesRecyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            val action = WorkoutSessionDetailFragmentDirections
                .actionWorkoutSessionDetailFragmentToWorkoutHistoryFragment()
            findNavController().navigate(action)


        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeWorkoutSession() }
                launch { observeExercises() }
                launch { observeUiState() }
            }
        }
    }

    private suspend fun observeWorkoutSession() {
        viewModel.workoutSession.collect { session ->
            session?.let {
                workoutTitle.text = it.name


                val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy 'at' HH:mm", Locale.getDefault())
                val durationMs = it.endTime?.minus(it.startTime) ?: 0L
                workoutDate.text = dateFormat.format(Date(it.startTime))
                workoutDuration.text = formatDuration(durationMs)
                burnedCalories.text = " Burned ${viewModel.totalCalories.value} kcal"
            }
        }
    }

    private suspend fun observeExercises() {
        viewModel.exercisesWithSets.collect { exercises ->
            exercisesAdapter.submitList(exercises)
            updateEmptyState(exercises.isEmpty())
        }
    }
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d minutes", minutes, seconds)
    }

    private suspend fun observeUiState() {
        viewModel.uiState.collect { state ->
            when (state) {
                is WorkoutSessionDetailViewModel.UiState.Error -> {
                    showError(state.message)
                }
                else -> { /* Handle other states if needed */ }
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

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}