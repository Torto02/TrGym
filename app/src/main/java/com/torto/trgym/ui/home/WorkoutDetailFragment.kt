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
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutExercisesAdapter
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.ui.dialogs.SelectExercisesDialog
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
            showSelectExercisesDialog()
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
            },
            onDeleteSet = { set ->
                showDeleteSetConfirmation(set)
            }
        )

        recyclerView.apply {
            adapter = exercisesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
            itemAnimator = null // Disabilita le animazioni per evitare problemi di ridimensionamento
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

    private fun showSelectExercisesDialog() {
        val dialog = SelectExercisesDialog()
        dialog.setOnExercisesSelectedListener { selectedExercises ->
            // Aggiungi tutti gli esercizi selezionati utilizzando il metodo ottimizzato
            val exerciseNames = selectedExercises.map { it.title }
            viewModel.addMultipleExercises(exerciseNames)

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
                viewModel.deleteExercise(exercise)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteSetConfirmation(set: ExerciseSet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina serie")
            .setMessage("Sei sicuro di voler eliminare la serie ${set.setNumber}?")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteExerciseSet(set)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}