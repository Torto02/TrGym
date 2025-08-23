package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutSessionWithCalories
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryAdapter(
    private val onWorkoutClick: (WorkoutSessionWithCalories) -> Unit
) : ListAdapter<WorkoutSessionWithCalories, WorkoutHistoryAdapter.WorkoutSessionViewHolder>(WorkoutSessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutSessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_session, parent, false)
        return WorkoutSessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutSessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WorkoutSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.workoutSessionCard)
        private val workoutName: TextView = itemView.findViewById(R.id.workoutName)
        private val workoutDate: TextView = itemView.findViewById(R.id.workoutDate)
        private val workoutDuration: TextView = itemView.findViewById(R.id.workoutDuration)
        private val burnedCalories: TextView = itemView.findViewById(R.id.burnedCalories)

        fun bind(workoutSessionWithCalories: WorkoutSessionWithCalories) {
            val workoutSession = workoutSessionWithCalories.workoutSession
            val calories = workoutSessionWithCalories.calories

            workoutName.text = workoutSession.name

            // Mostra solo giorno e mese
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            workoutDate.text = dateFormat.format(Date(workoutSession.startTime))

            val durationMs = workoutSession.endTime?.minus(workoutSession.startTime) ?: 0L
            workoutDuration.text = formatDuration(durationMs)

            // Mostra le calorie bruciate
            burnedCalories.text = "${calories} kCal"

            cardView.setOnClickListener {
                onWorkoutClick(workoutSessionWithCalories)
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            return String.format("%02d:%02d minutes", minutes, seconds)
        }
    }

    private class WorkoutSessionDiffCallback : DiffUtil.ItemCallback<WorkoutSessionWithCalories>() {
        override fun areItemsTheSame(oldItem: WorkoutSessionWithCalories, newItem: WorkoutSessionWithCalories): Boolean {
            return oldItem.workoutSession.id == newItem.workoutSession.id
        }

        override fun areContentsTheSame(oldItem: WorkoutSessionWithCalories, newItem: WorkoutSessionWithCalories): Boolean {
            return oldItem == newItem
        }
    }
}