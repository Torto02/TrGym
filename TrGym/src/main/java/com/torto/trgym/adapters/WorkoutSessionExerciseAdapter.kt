package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail

class WorkoutSessionExerciseAdapter : ListAdapter<WorkoutSessionExerciseDetail, WorkoutSessionExerciseAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_session_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseStatus: TextView = itemView.findViewById(R.id.exerciseStatus)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)

        private val setsAdapter = WorkoutSessionSetAdapter()

        init {
            setsRecyclerView.apply {
                adapter = setsAdapter
                layoutManager = LinearLayoutManager(itemView.context)
                isNestedScrollingEnabled = false
            }
        }

        fun bind(exerciseDetail: WorkoutSessionExerciseDetail) {
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets

            exerciseName.text = exercise.name

            // Status
            exerciseStatus.text = if (exercise.isCompleted) {
                "Completed"
            } else {
                "Not Completed"
            }

            val statusColor = if (exercise.isCompleted) {
                itemView.context.getColor(R.color.primary)
            } else {
                itemView.context.getColor(R.color.ios_secondaryLabel)
            }
            exerciseStatus.setTextColor(statusColor)

            // Sets
            setsAdapter.submitList(sets)

        }
    }

    private class ExerciseDiffCallback : DiffUtil.ItemCallback<WorkoutSessionExerciseDetail>() {
        override fun areItemsTheSame(oldItem: WorkoutSessionExerciseDetail, newItem: WorkoutSessionExerciseDetail): Boolean {
            return oldItem.exercise.id == newItem.exercise.id
        }

        override fun areContentsTheSame(oldItem: WorkoutSessionExerciseDetail, newItem: WorkoutSessionExerciseDetail): Boolean {
            return oldItem == newItem
        }
    }
}