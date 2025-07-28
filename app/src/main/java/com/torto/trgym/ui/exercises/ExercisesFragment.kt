package com.torto.trgym.ui.exercises

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.torto.trgym.adapters.ExercisesAdapter
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.viewmodel.exercises.ExercisesViewModel
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.torto.trgym.R

import android.view.Menu
import android.widget.PopupMenu
import android.widget.TextView

/**
 * Fragment per la visualizzazione degli esercizi.
 * Utilizza un RecyclerView per mostrare una lista di esercizi e include pulsanti per filtrare i dati.
 */
@AndroidEntryPoint
class ExercisesFragment : Fragment(R.layout.fragment_exercises) {



    // ViewModel per gestire lo stato dell'interfaccia utente e i dati degli esercizi.
    private val viewModel: ExercisesViewModel by activityViewModels()

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
        view.findViewById<RecyclerView>(R.id.rvExercises).apply {
            layoutManager = LinearLayoutManager(requireContext()) // Layout lineare per la lista.
            setHasFixedSize(true) // Ottimizza le prestazioni per dimensioni fisse.
            itemAnimator = null // Disabilita le animazioni per ridurre il jank.
            adapter = this@ExercisesFragment.adapter
        }

        val filterAllBtn = view.findViewById<MaterialCardView>(R.id.btnFilterAll)
        val btnFilterMuscles = view.findViewById<MaterialCardView>(R.id.btnFilterMuscles)
        val btnFilterEquipment = view.findViewById<MaterialCardView>(R.id.btnFilterEquipment)
        val selectedMuscle = view.findViewById<TextView>(R.id.selectedMuscle)
        val selectedEquipment = view.findViewById<TextView>(R.id.selectedEquipment)

        // Osserva e aggiorna i testi selezionati
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedMuscleName.collectLatest { selectedMuscle.text = it }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedEquipmentName.collectLatest { selectedEquipment.text = it }
            }
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

        btnFilterMuscles.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val muscleGroups = viewModel.muscleGroups.first()
                val popup = PopupMenu(requireContext(), it)  // “it” è il btnFilterMuscles

                // Add default "All muscles" item
                popup.menu.add(Menu.NONE, -1, 0, getString(R.string.all_muscles))

                // Aggiungi le voci al menu
                muscleGroups.forEachIndexed { index, mg ->
                    popup.menu.add(Menu.NONE, index, index, mg.name)
                }

                // Gestisci la selezione
                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == -1) {
                        viewModel.clearMuscleGroupFilter()
                        return@setOnMenuItemClickListener true

                    }
                    val selected = muscleGroups[item.itemId]
                    viewModel.filterByMuscleGroup(selected.muscleGroupId)
                    true
                }

                popup.show()
            }
        }
        btnFilterEquipment.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val equipment = viewModel.equipments.first()
                val popup = PopupMenu(requireContext(), it)  // “it” è il btnFilterEquipment

                // Add default "All equipment" item
                popup.menu.add(Menu.NONE, -1, 0, getString(R.string.all_equipment))

                equipment.forEachIndexed { index, eq ->
                    popup.menu.add(Menu.NONE, index, index, eq.name)
                }
                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == -1) {
                        viewModel.clearEquipmentFilter()
                        return@setOnMenuItemClickListener true
                    }
                    val selected = equipment[item.itemId]
                    viewModel.filterByEquipment(selected.equipment_id)
                    true
                }
                popup.show()
            }
        }
        filterAllBtn.setOnClickListener {
            viewModel.clearMuscleGroupFilter()
            viewModel.clearEquipmentFilter()
        }
    }
}