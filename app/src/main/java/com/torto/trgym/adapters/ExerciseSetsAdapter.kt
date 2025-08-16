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



        fun bind(set: ExerciseSet) {
            isBindingData = true
            currentSet = set

            // Reset pending changes
            pendingWeight = set.weightKg
            pendingReps = set.reps

            setNumber.text = set.setNumber.toString()
            weightInput.setText(set.weightKg?.toString() ?: "")
            repsInput.setText(set.reps?.toString() ?: "")

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

        private fun setupTextWatchersIfEditMode() {
            if (!isEditMode) return

            weightInput.addCustomTextWatcher(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isBindingData) return

                    val weight = s?.toString()?.toDoubleOrNull()
                    pendingWeight = weight

                    // Debounce update
                    scheduleUpdate()
                }
            })

            repsInput.addCustomTextWatcher(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isBindingData) return

                    val reps = s?.toString()?.toIntOrNull()
                    pendingReps = reps

                    // Debounce update
                    scheduleUpdate()
                }
            })
        }

        private fun scheduleUpdate() {
            // Cancel previous update job
            updateJob?.cancel()

            // Schedule new update with debouncing
            updateJob = updateScope.launch {
                delay(300) // 300ms debounce

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
                    // Animate removal
//                    animateRemoval {
                        onDeleteSet(set)
//                    }
                }
            }
        }


        fun cleanup() {
            updateJob?.cancel()
        }
    }

    private fun TextInputEditText.addCustomTextWatcher(watcher: TextWatcher) {
        addTextChangedListener(watcher)
    }

    override fun onViewRecycled(holder: SetViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    class SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem == newItem
        }
    }
}