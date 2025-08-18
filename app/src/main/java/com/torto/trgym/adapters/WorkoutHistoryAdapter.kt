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
import com.torto.trgym.database.entities.WorkoutSession
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryAdapter(
    private val onWorkoutClick: (WorkoutSession) -> Unit
) : ListAdapter<WorkoutSession, WorkoutHistoryAdapter.WorkoutSessionViewHolder>(WorkoutSessionDiffCallback()) {

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
        private val workoutStatus: TextView = itemView.findViewById(R.id.workoutStatus)

        fun bind(workoutSession: WorkoutSession) {
            workoutName.text = workoutSession.name

            // Format date
            val dateFormat = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault())
            workoutDate.text = dateFormat.format(Date(workoutSession.startTime))

            // Format duration
            workoutDuration.text = if (workoutSession.totalDurationMs != null) {
                formatDuration(workoutSession.totalDurationMs)
            } else {
                "Durata non disponibile"
            }

            // Status
            workoutStatus.text = if (workoutSession.isCompleted) {
                "Completato"
            } else {
                "Non completato"
            }

            // Set status color
            val statusColor = if (workoutSession.isCompleted) {
                itemView.context.getColor(R.color.primary)
            } else {
                itemView.context.getColor(R.color.ios_secondaryLabel)
            }
            workoutStatus.setTextColor(statusColor)

            cardView.setOnClickListener {
                onWorkoutClick(workoutSession)
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private class WorkoutSessionDiffCallback : DiffUtil.ItemCallback<WorkoutSession>() {
        override fun areItemsTheSame(oldItem: WorkoutSession, newItem: WorkoutSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WorkoutSession, newItem: WorkoutSession): Boolean {
            return oldItem == newItem
        }
    }
}