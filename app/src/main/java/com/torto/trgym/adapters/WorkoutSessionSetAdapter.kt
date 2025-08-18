package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.database.entities.WorkoutSessionSet

class WorkoutSessionSetAdapter : ListAdapter<WorkoutSessionSet, WorkoutSessionSetAdapter.SetViewHolder>(SetDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_session_set, parent, false)
        return SetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
        private val targetWeight: TextView = itemView.findViewById(R.id.targetWeight)
        private val actualWeight: TextView = itemView.findViewById(R.id.actualWeight)
        private val targetReps: TextView = itemView.findViewById(R.id.targetReps)
        private val actualReps: TextView = itemView.findViewById(R.id.actualReps)
        private val setStatus: TextView = itemView.findViewById(R.id.setStatus)

        fun bind(set: WorkoutSessionSet) {
            setNumber.text = "Set ${set.setNumber}"

            // Target values
            targetWeight.text = set.targetWeightKg?.let { "${it}kg" } ?: "-"
            targetReps.text = set.targetReps?.toString() ?: "-"

            // Actual values
            actualWeight.text = set.actualWeightKg?.let { "${it}kg" } ?: "-"
            actualReps.text = set.actualReps?.toString() ?: "-"

            // Status indicator
            if (set.isCompleted) {
                setStatus.text = "✓"
                setStatus.setTextColor(itemView.context.getColor(R.color.primary))

                // Highlight actual values if completed
                actualWeight.setTextColor(itemView.context.getColor(R.color.ios_label))
                actualReps.setTextColor(itemView.context.getColor(R.color.ios_label))
            } else {
                setStatus.text = "○"
                setStatus.setTextColor(itemView.context.getColor(R.color.ios_secondaryLabel))

                // Dim actual values if not completed
                actualWeight.setTextColor(itemView.context.getColor(R.color.ios_secondaryLabel))
                actualReps.setTextColor(itemView.context.getColor(R.color.ios_secondaryLabel))
            }
        }
    }

    private class SetDiffCallback : DiffUtil.ItemCallback<WorkoutSessionSet>() {
        override fun areItemsTheSame(oldItem: WorkoutSessionSet, newItem: WorkoutSessionSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WorkoutSessionSet, newItem: WorkoutSessionSet): Boolean {
            return oldItem == newItem
        }
    }
}