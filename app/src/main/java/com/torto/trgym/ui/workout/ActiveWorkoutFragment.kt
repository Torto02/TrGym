package com.torto.trgym.ui.workout

import android.os.Bundle
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.torto.trgym.R
import com.torto.trgym.adapters.ActiveWorkoutExercisesAdapter
import com.torto.trgym.viewmodel.workout.ActiveWorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        setupBackPressHandler()
    }

    private fun initViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        workoutTitle = view.findViewById(R.id.workoutTitle)
        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        completeWorkoutButton = view.findViewById(R.id.completeWorkoutButton)
        cancelWorkoutButton = view.findViewById(R.id.cancelWorkoutButton)
        pauseResumeButton = view.findViewById(R.id.pauseResumeButton)
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
}