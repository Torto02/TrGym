package com.torto.trgym.ui.dialogs

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.torto.trgym.R
import com.torto.trgym.adapters.SelectExercisesAdapter
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.viewmodel.exercises.ExercisesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.view.Menu
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

@AndroidEntryPoint
class SelectExercisesDialog : DialogFragment() {

    private val viewModel: ExercisesViewModel by activityViewModels()
    private lateinit var adapter: SelectExercisesAdapter
    private var onExercisesSelected: ((List<ExerciseWithImage>) -> Unit)? = null

    fun setOnExercisesSelectedListener(listener: (List<ExerciseWithImage>) -> Unit) {
        onExercisesSelected = listener
    }



    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Inflate custom view and initialize
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_exercises, null)
        setupViews(view)
        setupRecyclerView(view)
        observeViewModel()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    private fun setupViews(view: View) {
        val filterAllBtn = view.findViewById<MaterialCardView>(R.id.btnFilterAll)
        val btnFilterMuscles = view.findViewById<MaterialCardView>(R.id.btnFilterMuscles)
        val btnFilterEquipment = view.findViewById<MaterialCardView>(R.id.btnFilterEquipment)
        val selectedMuscle = view.findViewById<TextView>(R.id.selectedMuscle)
        val selectedEquipment = view.findViewById<TextView>(R.id.selectedEquipment)
        val starBtn = view.findViewById<ImageView>(R.id.starButton)
        val addButton = view.findViewById<MaterialButton>(R.id.btnAdd)
        val cancelButton = view.findViewById<MaterialButton>(R.id.btnCancel)


        // Osserva e aggiorna i testi selezionati
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedMuscleName.collectLatest { selectedMuscle.text = it }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedEquipmentName.collectLatest { selectedEquipment.text = it }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.starredFilter.collectLatest { isStarred ->
                    updateStarButton(starBtn, isStarred)
                }
            }
        }

        btnFilterMuscles.setOnClickListener {
            Log.d("SelectExercisesDialog", "Filter by muscles clicked")
            lifecycleScope.launch {
                val muscleGroups = viewModel.muscleGroups.first()
                val popup = PopupMenu(requireContext(), it)

                popup.menu.add(Menu.NONE, -1, 0, getString(R.string.all_muscles))

                muscleGroups.forEachIndexed { index, mg ->
                    popup.menu.add(Menu.NONE, index, index, mg.name)
                }

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
            lifecycleScope.launch {
                val equipment = viewModel.equipments.first()
                val popup = PopupMenu(requireContext(), it)

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
            lifecycleScope.launch {
                viewModel.clearAllFilters()
            }
        }

        starBtn.setOnClickListener {
            viewModel.starredExercise()
            updateStarButton(starBtn, viewModel.starredFilter.value)
        }

        addButton.setOnClickListener {
            val selectedExercises = adapter.getSelectedExercises()
            if (selectedExercises.isNotEmpty()) {
                onExercisesSelected?.invoke(selectedExercises)
                dismiss()
            }
        }

        cancelButton.setOnClickListener {
            Log.d("SelectExercisesDialog", "Cancel button clicked")
            dismiss()
        }
    }

    private fun setupRecyclerView(dialogView: View) {
        adapter = SelectExercisesAdapter { selectedCount ->
            // Aggiorna il testo del bottone con il numero di esercizi selezionati
            val addButton = dialogView.findViewById<MaterialButton>(R.id.btnAdd)
            addButton.text = if (selectedCount > 0) {
                "Aggiungi ($selectedCount)"
            } else {
                "Aggiungi"
            }
            addButton.isEnabled = selectedCount > 0
        }

        dialogView.findViewById<RecyclerView>(R.id.rvExercises).apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            itemAnimator = null
            adapter = this@SelectExercisesDialog.adapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is ExercisesViewModel.ExercisesUiState.Data) {
                        adapter.submitList(state.exercises)
                    }
                }
            }
        }
    }

    private fun updateStarButton(starButton: ImageView, isStarred: Boolean) {
        if (isStarred) {
            starButton.setImageResource(R.drawable.ic_star_filled)
            starButton.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
        } else {
            starButton.setImageResource(R.drawable.ic_star_outline)
            starButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ios_secondaryLabel))
        }
    }

    override fun onStart() {
        super.onStart()
        // Imposta le dimensioni del dialog per essere più grande
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 1).toInt(),
            (resources.displayMetrics.heightPixels * 0.9).toInt()
        )
    }


}