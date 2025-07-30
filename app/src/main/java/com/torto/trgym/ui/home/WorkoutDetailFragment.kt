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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutExercisesAdapter
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.viewmodel.home.WorkoutDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WorkoutDetailFragment : Fragment(R.layout.fragment_workout_detail) {

    private val viewModel: WorkoutDetailViewModel by viewModels()
    private lateinit var exercisesAdapter: WorkoutExercisesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView(view)
        observeViewModel()
    }

    private fun setupViews(view: View) {
        val backButton = view.findViewById<TextView>(R.id.backButton)
        val addExerciseBtn = view.findViewById<FloatingActionButton>(R.id.addExerciseBtn)

        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        addExerciseBtn.setOnClickListener {
            showAddExerciseDialog()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.exercisesRecyclerView)
        exercisesAdapter = WorkoutExercisesAdapter(
            onAddSetClick = { exerciseId ->
                viewModel.addSetToExercise(exerciseId)
            },
            onSetChanged = { set ->
                viewModel.updateExerciseSet(set)
            },
            onDeleteExercise = { exercise ->
                showDeleteExerciseConfirmation(exercise)
            }
        )

        recyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel() {
        // Osserva il titolo della scheda
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.workoutCard.collect { workoutCard ->
                    workoutCard?.let {
                        view?.findViewById<TextView>(R.id.workoutTitle)?.text = it.name
                    }
                }
            }
        }

        // Osserva gli esercizi
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exercisesWithSets.collect { exercisesWithSets ->
                    exercisesAdapter.submitList(exercisesWithSets)
                }
            }
        }

        // Osserva lo stato UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is WorkoutDetailViewModel.UiState.Loading -> {
                            // Mostra loading se necessario
                        }
                        is WorkoutDetailViewModel.UiState.Success -> {
                            // UI aggiornata con successo
                        }
                        is WorkoutDetailViewModel.UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showAddExerciseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_exercise, null)
        val exerciseNameEditText = dialogView.findViewById<TextInputEditText>(R.id.exerciseNameEditText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Aggiungi esercizio")
            .setView(dialogView)
            .setPositiveButton("Aggiungi") { _, _ ->
                val exerciseName = exerciseNameEditText.text.toString().trim()
                if (exerciseName.isNotEmpty()) {
                    viewModel.addExercise(exerciseName)
                } else {
                    Toast.makeText(requireContext(), "Inserisci il nome dell'esercizio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteExerciseConfirmation(exercise: WorkoutExercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina esercizio")
            .setMessage("Sei sicuro di voler eliminare \"${exercise.name}\"?")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteExercise(exercise)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}