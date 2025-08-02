package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.databinding.ItemSelectExerciseBinding
import coil.load
import com.torto.trgym.R

class SelectExercisesAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ExerciseWithImage, SelectExercisesAdapter.SelectExerciseViewHolder>(Diff) {

    private val selectedExercises = mutableSetOf<Int>()

    object Diff : DiffUtil.ItemCallback<ExerciseWithImage>() {
        override fun areItemsTheSame(o: ExerciseWithImage, n: ExerciseWithImage) = o.id == n.id
        override fun areContentsTheSame(o: ExerciseWithImage, n: ExerciseWithImage) = o == n
    }

    inner class SelectExerciseViewHolder(private val binding: ItemSelectExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExerciseWithImage) {
            val defaultRes = R.drawable.ic_fitness_center

            // Carica l'immagine
            val fileName = item.imagePath
            if (!fileName.isNullOrBlank()) {
                val assetUri = "file:///android_asset/images/$fileName"
                binding.ExercisePrev.load(assetUri) {
                    crossfade(true)
                    error(defaultRes)
                    placeholder(defaultRes)
                }
            } else {
                binding.ExercisePrev.load(defaultRes) {
                    crossfade(true)
                }
            }

            binding.ExerciseName.text = item.title
            binding.ExerciseType.text = item.type

            // Gestisci lo stato di selezione
            val isSelected = selectedExercises.contains(item.id)
            updateSelectionState(isSelected)

            // Click listener per la selezione
            binding.root.setOnClickListener {
                if (selectedExercises.contains(item.id)) {
                    selectedExercises.remove(item.id)
                } else {
                    selectedExercises.add(item.id)
                }
                updateSelectionState(selectedExercises.contains(item.id))
                onSelectionChanged(selectedExercises.size)
            }
        }

        private fun updateSelectionState(isSelected: Boolean) {
            if (isSelected) {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.selected_exercise_background)
                )
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.selected_exercise_stroke)
                binding.root.strokeWidth = 4
                binding.selectionIndicator.visibility = android.view.View.VISIBLE
            } else {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, android.R.color.transparent)
                binding.root.strokeWidth = 0
                binding.selectionIndicator.visibility = android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectExerciseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSelectExerciseBinding.inflate(inflater, parent, false)
        return SelectExerciseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SelectExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedExercises(): List<ExerciseWithImage> {
        return currentList.filter { selectedExercises.contains(it.id) }
    }

    fun clearSelection() {
        selectedExercises.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
}