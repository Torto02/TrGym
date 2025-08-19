
package com.torto.trgym.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.database.entities.ExerciseSet
import kotlinx.coroutines.*

class ExerciseSetsAdapter(
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<ExerciseSet, ExerciseSetsAdapter.SetViewHolder>(SetDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    private var isEditMode = false

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_set, parent, false)
        return SetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update - only update specific fields
            val set = getItem(position)
            for (payload in payloads) {
                when (payload) {
                    is SetUpdatePayload -> {
                        holder.updateFromPayload(set, payload)
                    }
                }
            }
        }
    }

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
        private val weightInput: TextInputEditText = itemView.findViewById(R.id.weightInput)
        private val repsInput: TextInputEditText = itemView.findViewById(R.id.repsInput)
        private val setMenu: ImageView = itemView.findViewById(R.id.setMenu)
        private val setCompletedCheckbox: MaterialCheckBox = itemView.findViewById(R.id.setCompletedCheckbox)

        private var currentSet: ExerciseSet? = null
        private var isBindingData = false

        // Coroutine per debouncing delle modifiche
        private var updateJob: Job? = null
        private val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Cache locale per le modifiche pending
        private var pendingWeight: Double? = null
        private var pendingReps: Int? = null

        // TextWatcher references to avoid multiple registrations
        private var weightTextWatcher: TextWatcher? = null
        private var repsTextWatcher: TextWatcher? = null

        fun bind(set: ExerciseSet) {
            isBindingData = true
            currentSet = set

            // Reset pending changes
            pendingWeight = set.weightKg
            pendingReps = set.reps

            setNumber.text = set.setNumber.toString()

            // Only update text if it's actually different to avoid cursor movement
            if (weightInput.text.toString() != (set.weightKg?.toString() ?: "")) {
                weightInput.setText(set.weightKg?.toString() ?: "")
            }
            if (repsInput.text.toString() != (set.reps?.toString() ?: "")) {
                repsInput.setText(set.reps?.toString() ?: "")
            }

            // Configure edit mode
            weightInput.isEnabled = isEditMode
            repsInput.isEnabled = isEditMode
            setMenu.visibility = if (isEditMode) View.VISIBLE else View.GONE
            setCompletedCheckbox.visibility = View.GONE

            // Set visual cues for edit mode
            if (isEditMode) {
                // In edit mode, make inputs more prominent
                weightInput.alpha = 1.0f
                repsInput.alpha = 1.0f
                weightInput.setBackgroundResource(R.drawable.bg_input_field_editing)
                repsInput.setBackgroundResource(R.drawable.bg_input_field_editing)
            } else {
                // Normal mode
                weightInput.alpha = 0.9f
                repsInput.alpha = 0.9f
                weightInput.setBackgroundResource(R.drawable.bg_input_field)
                repsInput.setBackgroundResource(R.drawable.bg_input_field)
            }

            isBindingData = false
            setupTextWatchersIfEditMode()
        }

        fun updateFromPayload(set: ExerciseSet, payload: SetUpdatePayload) {
            currentSet = set

            // Only update the fields that are not currently being edited
            if (!weightInput.hasFocus() && payload.updateWeight) {
                if (weightInput.text.toString() != (set.weightKg?.toString() ?: "")) {
                    isBindingData = true
                    weightInput.setText(set.weightKg?.toString() ?: "")
                    isBindingData = false
                }
                pendingWeight = set.weightKg
            }

            if (!repsInput.hasFocus() && payload.updateReps) {
                if (repsInput.text.toString() != (set.reps?.toString() ?: "")) {
                    isBindingData = true
                    repsInput.setText(set.reps?.toString() ?: "")
                    isBindingData = false
                }
                pendingReps = set.reps
            }
        }

        private fun setupTextWatchersIfEditMode() {
            if (!isEditMode) {
                clearTextWatchers()
                return
            }

            // Remove existing watchers to avoid duplicates
            clearTextWatchers()

            weightTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isBindingData) return

                    val weight = s?.toString()?.toDoubleOrNull()
                    pendingWeight = weight

                    // Debounce update
                    scheduleUpdate()
                }
            }

            repsTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isBindingData) return

                    val reps = s?.toString()?.toIntOrNull()
                    pendingReps = reps

                    // Debounce update
                    scheduleUpdate()
                }
            }

            weightInput.addTextChangedListener(weightTextWatcher)
            repsInput.addTextChangedListener(repsTextWatcher)
        }

        private fun clearTextWatchers() {
            weightTextWatcher?.let { weightInput.removeTextChangedListener(it) }
            repsTextWatcher?.let { repsInput.removeTextChangedListener(it) }
            weightTextWatcher = null
            repsTextWatcher = null
        }

        private fun scheduleUpdate() {
            // Cancel previous update job
            updateJob?.cancel()

            // Schedule new update with debouncing
            updateJob = updateScope.launch {
                delay(500) // Increased debounce time to reduce frequent updates

                currentSet?.let { set ->
                    val hasWeightChanged = set.weightKg != pendingWeight
                    val hasRepsChanged = set.reps != pendingReps

                    if (hasWeightChanged || hasRepsChanged) {
                        val updatedSet = set.copy(
                            weightKg = pendingWeight,
                            reps = pendingReps,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSetChanged(updatedSet)
                    }
                }
            }
        }

        init {
            setMenu.setOnClickListener {
                currentSet?.let { set ->
                    onDeleteSet(set)
                }
            }
        }

        fun cleanup() {
            updateJob?.cancel()
            clearTextWatchers()
        }
    }

    override fun onViewRecycled(holder: SetViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    // Payload class for partial updates
    data class SetUpdatePayload(
        val updateWeight: Boolean = true,
        val updateReps: Boolean = true
    )

    class SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: ExerciseSet, newItem: ExerciseSet): Any? {
            if (oldItem.id != newItem.id) return null

            val updateWeight = oldItem.weightKg != newItem.weightKg
            val updateReps = oldItem.reps != newItem.reps

            return if (updateWeight || updateReps) {
                SetUpdatePayload(updateWeight, updateReps)
            } else null
        }
    }
}