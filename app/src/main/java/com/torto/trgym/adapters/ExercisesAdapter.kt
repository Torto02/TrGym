package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.databinding.ItemExerciseBinding
import coil.load
import com.torto.trgym.ui.exercises.ExercisesFragmentDirections

class ExercisesAdapter :
    ListAdapter<ExerciseWithImage, ExercisesAdapter.ExViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ExerciseWithImage>() {
        override fun areItemsTheSame(o: ExerciseWithImage, n: ExerciseWithImage) = o.id == n.id
        override fun areContentsTheSame(o: ExerciseWithImage, n: ExerciseWithImage) = o == n
    }

    inner class ExViewHolder(private val binding: ItemExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExerciseWithImage) {
            val defaultRes = com.torto.trgym.R.drawable.ic_fitness_center

            // Sempre usa Coil per caricare l'immagine, anche per il default
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
            binding.root.setOnClickListener {
                val action = ExercisesFragmentDirections
                    .actionExercisesFragmentToExerciseDetailFragment(item.id,)
                it.findNavController().navigate(action)


            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemExerciseBinding.inflate(inflater, parent, false)
        return ExViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}