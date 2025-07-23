package com.torto.trgym.ui.exercises

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

import com.torto.trgym.R
import dagger.hilt.android.AndroidEntryPoint
import com.torto.trgym.viewmodel.exercises.ExerciseDetailViewModel
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import coil.load
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialContainerTransform

import kotlinx.coroutines.launch

/**
 * Fragment per la visualizzazione dei dettagli di un esercizio.
 * Utilizza un ViewModel per gestire lo stato dell'interfaccia utente e i dati dell'esercizio.
 */
@AndroidEntryPoint
class ExerciseDetailFragment : Fragment(R.layout.fragment_exercise_detail) {





    // Argomenti passati al Fragment tramite Navigation Component.
    private val args: ExerciseDetailFragmentArgs by navArgs()

    // ViewModel per gestire lo stato dell'interfaccia utente e i dati dell'esercizio.
    private val vm: ExerciseDetailViewModel by viewModels()

    /**
     * Metodo chiamato quando la vista del Fragment è stata creata.
     * Configura i componenti della vista e avvia la raccolta dello stato del ViewModel.
     *
     * @param view La vista associata al Fragment.
     * @param savedInstanceState Lo stato salvato del Fragment, se disponibile.
     */




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Recupera i riferimenti ai componenti della vista.
        val anim = view.findViewById<ImageView>(R.id.detailImage)
        val name = view.findViewById<TextView>(R.id.detailName)
        val type = view.findViewById<TextView>(R.id.detailType)
        val tips = view.findViewById<TextView>(R.id.detailTips)
        val muscles = view.findViewById<TextView>(R.id.detailMuscles)
        val equip = view.findViewById<TextView>(R.id.detailEquipment)

        // Avvia la raccolta dello stato del ViewModel.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    when (state) {
                        is ExerciseDetailViewModel.UiState.Loading -> {}
                        is ExerciseDetailViewModel.UiState.Success -> {
                            val d = state.detail
                            name.text = d.exercise.name
                            type.text = d.exercise.type
                            tips.text = d.steps.mapIndexed { index, step -> "${index + 1}. ${step.description}" }.joinToString("\n")
                            muscles.text = d.muscles.joinToString(", ") { it.name }
                            equip.text = d.equipment.joinToString(", ") { it.name }

                            val gif = "file:///android_asset/gif/${d.exercise.name}.gif"
                            Glide.with(requireContext()).load(gif).into(anim)
                        }
                        is ExerciseDetailViewModel.UiState.Error -> {

                            Toast.makeText(requireContext(), state.msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

