package com.torto.trgym.ui.workout

import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutHistoryAdapter
import com.torto.trgym.database.dto.WorkoutSessionWithCalories
import com.torto.trgym.viewmodel.workout.WorkoutHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WorkoutHistoryFragment : Fragment(R.layout.fragment_workout_history) {

    private val viewModel: WorkoutHistoryViewModel by viewModels()
    private lateinit var historyAdapter: WorkoutHistoryAdapter

    // Views
    private val backButton by lazy { requireView().findViewById<TextView>(R.id.backButton) }
    private val historyRecyclerView by lazy { requireView().findViewById<RecyclerView>(R.id.historyRecyclerView) }
    private val emptyStateLayout by lazy { requireView().findViewById<View>(R.id.emptyStateLayout) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            val action = WorkoutHistoryFragmentDirections
                .actionWorkoutHistoryFragmentToProfileFragment()
            findNavController().navigate(action)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        historyAdapter = WorkoutHistoryAdapter { workoutSessionWithCalories ->
            navigateToWorkoutDetail(workoutSessionWithCalories)
        }

        historyRecyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            val action = WorkoutHistoryFragmentDirections.actionWorkoutHistoryFragmentToProfileFragment()
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeWorkoutSessions() }
                launch { observeUiState() }
            }
        }
    }

    private suspend fun observeWorkoutSessions() {
        viewModel.workoutSessions.collect { sessions ->
            historyAdapter.submitList(sessions)
            updateEmptyState(sessions.isEmpty())
        }
    }

    private suspend fun observeUiState() {
        viewModel.uiState.collect { state ->
            when (state) {
                is WorkoutHistoryViewModel.UiState.Error -> {
                    showError(state.message)
                }
                else -> { /* Handle other states if needed */ }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateLayout.visibility = View.VISIBLE
            historyRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun navigateToWorkoutDetail(workoutSessionWithCalories: WorkoutSessionWithCalories) {
        val action = WorkoutHistoryFragmentDirections
            .actionWorkoutHistoryFragmentToWorkoutSessionDetailFragment(workoutSessionWithCalories.workoutSession.id)
        findNavController().navigate(action)
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}