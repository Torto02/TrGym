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
        private val weigth: TextView = itemView.findViewById(R.id.weight)
        private val reps: TextView = itemView.findViewById(R.id.reps)

        fun bind(set: WorkoutSessionSet) {
            setNumber.text = "${set.setNumber}"

            // Target values
            weigth.text = set.targetWeightKg?.let { "${it}kg" } ?: "-"
            reps.text = set.targetReps?.toString() ?: "-"

            // Actual values



            // Status indicator

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