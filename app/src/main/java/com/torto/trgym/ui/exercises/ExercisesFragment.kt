package com.torto.trgym.ui.exercises

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.torto.trgym.adapters.ExercisesAdapter
import dagger.hilt.android.AndroidEntryPoint

import com.torto.trgym.viewmodel.exercises.ExercisesViewModel
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import android.widget.Button
import com.torto.trgym.R

/**
 * Fragment per la visualizzazione degli esercizi.
 * Utilizza un RecyclerView per mostrare una lista di esercizi e include pulsanti per filtrare i dati.
 */
@AndroidEntryPoint
class ExercisesFragment : Fragment(R.layout.fragment_exercises) {



    // ViewModel per gestire lo stato dell'interfaccia utente e i dati degli esercizi.
    private val viewModel: ExercisesViewModel by viewModels()
    private lateinit var adapter: ExercisesAdapter

    /**
     * Metodo chiamato quando la vista del Fragment è stata creata.
     * Configura il RecyclerView e avvia la raccolta dello stato dell'interfaccia utente.
     *
     * @param view La vista associata al Fragment.
     * @param savedInstanceState Lo stato salvato del Fragment, se disponibile.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Configura l'adapter per il RecyclerView.
        adapter = ExercisesAdapter()

        // Configura il RecyclerView per mostrare gli esercizi.
        val rv = view.findViewById<RecyclerView>(R.id.rvExercises).apply {
            layoutManager = LinearLayoutManager(requireContext()) // Layout lineare per la lista.
            setHasFixedSize(true) // Ottimizza le prestazioni per dimensioni fisse.
            itemAnimator = null // Disabilita le animazioni per ridurre il jank.
            adapter = this@ExercisesFragment.adapter
        }

        // Colleziona lo stato dell'interfaccia utente dal ViewModel.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is ExercisesViewModel.ExercisesUiState.Data) {
                        // Aggiorna la lista degli esercizi nell'adapter.
                        adapter.submitList(state.exercises)
                    }
                }
            }
        }

        // Configura i listener per i pulsanti di filtro (placeholder).
        view.findViewById<Button>(R.id.btnFilterMuscles).setOnClickListener { /* TODO */ }
        view.findViewById<Button>(R.id.btnFilterEquipment).setOnClickListener { /* TODO */ }
    }
}