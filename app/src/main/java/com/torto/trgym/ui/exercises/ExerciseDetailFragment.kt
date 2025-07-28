package com.torto.trgym.ui.exercises


import android.os.Bundle
import android.util.Log
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import android.widget.LinearLayout

import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExerciseDetailFragment : Fragment(R.layout.fragment_exercise_detail) {
    private val args: ExerciseDetailFragmentArgs by navArgs()

    private val vm: ExerciseDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val anim = view.findViewById<ImageView>(R.id.detailImage)
        val name = view.findViewById<TextView>(R.id.detailName)
        val type = view.findViewById<TextView>(R.id.detailType)
        val tips = view.findViewById<TextView>(R.id.detailTips)
        val muscles = view.findViewById<TextView>(R.id.detailMuscles)
        val equip = view.findViewById<TextView>(R.id.detailEquipment)
        val backBtn = view.findViewById<LinearLayout>(R.id.backButton)

        // Avvia la raccolta dello stato del ViewModel.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    when (state) {
                        is ExerciseDetailViewModel.UiState.Loading -> {}
                        is ExerciseDetailViewModel.UiState.Success -> {
                            Log.d("ExerciseDetailFragment", "Received exercise detail: ${state.detail}")
                            val d = state.detail
                            name.text = d.translations[0].title // Assuming the first translation is the default
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
        backBtn.setOnClickListener {
            findNavController().popBackStack()
            Log.d("ExerciseDetailFragment", "Back button clicked")
        }
    }
}

