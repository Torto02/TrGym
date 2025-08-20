package com.torto.trgym.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.torto.trgym.R
import com.torto.trgym.database.entities.ExerciseSet

class ActiveWorkoutSetsAdapter(
    private val onSetCompletionChanged: (String, Boolean) -> Unit,
    private val onSetValueChanged: (String, Double?, Int?) -> Unit,
    private val onSetMenuClicked: ((String) -> Unit)? = null,
    private val onSetValueChangedSimple: ((String, Double?, Int?) -> Unit)? = null
) : ListAdapter<ExerciseSet, ActiveWorkoutSetsAdapter.SetViewHolder>(SetDiffCallback()) {

    private val completedSets = mutableMapOf<String, Boolean>()
    private val setIdMapping = mutableMapOf<String, String>()

    fun updateCompletedSets(setCompletionMap: Map<String, Boolean>, mapping: Map<String, String>) {
        val oldCompletedSets = completedSets.toMap()
        val oldSetIdMapping = setIdMapping.toMap()
        
        completedSets.clear()
        completedSets.putAll(setCompletionMap)

        setIdMapping.clear()
        setIdMapping.putAll(mapping)

        // Only update items where completion status actually changed
        var hasChanges = false
        for (position in 0 until itemCount) {
            val set = getItem(position)
            val sessionSetId = setIdMapping[set.id] ?: set.id
            val oldCompleted = oldCompletedSets[sessionSetId] ?: false
            val newCompleted = completedSets[sessionSetId] ?: false
            
            if (oldCompleted != newCompleted) {
                notifyItemChanged(position, "completion_changed")
                hasChanges = true
            }
        }
        
        // Only notify if mappings changed and no individual items were updated
        if (!hasChanges && oldSetIdMapping != setIdMapping) {
            notifyItemRangeChanged(0, itemCount, "mapping_changed")
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
        if (payloads.isNotEmpty()) {
            // Handle partial updates for better performance
            val payload = payloads[0] as? String
            when (payload) {
                "completion_changed" -> holder.updateCompletionOnly(getItem(position))
                "mapping_changed" -> holder.updateMappingOnly(getItem(position))
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
        private val weightInput: com.google.android.material.textfield.TextInputEditText =
            itemView.findViewById(R.id.weightInput)
        private val repsInput: com.google.android.material.textfield.TextInputEditText =
            itemView.findViewById(R.id.repsInput)
        private val setCompletedCheckbox: MaterialCheckBox =
            itemView.findViewById(R.id.setCompletedCheckbox)
        private val setMenu: ImageView = itemView.findViewById(R.id.setMenu)

        private var currentSet: ExerciseSet? = null
        private var isBindingData = false

        init {
            setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (!isBindingData) {
                    currentSet?.let { set ->
                        // Use the session set ID invece of the original set ID
                        val sessionSetId = setIdMapping[set.id] ?: set.id
                        onSetCompletionChanged(sessionSetId, isChecked)
                    }
                }
            }


        }

        fun bind(set: ExerciseSet) {
            isBindingData = true
            currentSet = set

            setNumber.text = set.setNumber.toString()
            weightInput.setText(set.weightKg?.toString() ?: "")
            repsInput.setText(set.reps?.toString() ?: "")


            // Nascondi sempre gli elementi di edit mode
            setMenu.visibility = View.GONE
            setCompletedCheckbox.visibility = View.VISIBLE

            // Get session set completion state - ensure it defaults to unchecked
            val sessionSetId = setIdMapping[set.id] ?: set.id
            val isCompleted = completedSets[sessionSetId] ?: false
            setCompletedCheckbox.isChecked = isCompleted

            // Highlight the entire row if completed
            itemView.background.setTint(
                if (isCompleted)
                    ContextCompat.getColor(itemView.context, R.color.completed_set_background)
                else
                    ContextCompat.getColor(itemView.context, R.color.transparent)
            )

            // Abilita gli input durante l'allenamento attivo
            weightInput.isEnabled = false
            repsInput.isEnabled = false

            isBindingData = false
        }
        
        fun updateCompletionOnly(set: ExerciseSet) {
            isBindingData = true
            currentSet = set
            
            // Only update checkbox state and background
            val sessionSetId = setIdMapping[set.id] ?: set.id
            val isCompleted = completedSets[sessionSetId] ?: false
            setCompletedCheckbox.isChecked = isCompleted
            
            // Update background color
            itemView.background.setTint(
                if (isCompleted)
                    ContextCompat.getColor(itemView.context, R.color.completed_set_background)
                else
                    ContextCompat.getColor(itemView.context, R.color.transparent)
            )
            
            isBindingData = false
        }
        
        fun updateMappingOnly(set: ExerciseSet) {
            // Just update the current set reference, no UI changes needed
            currentSet = set
        }

        private fun updateSetValues() {
            currentSet?.let { set ->
                val weight = weightInput.text.toString().toDoubleOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                // Use the session set ID instead of the original set ID
                val sessionSetId = setIdMapping[set.id]
                if (sessionSetId != null) {
                    onSetValueChangedSimple?.invoke(sessionSetId, weight, reps) ?: onSetValueChanged(sessionSetId, weight, reps)
                }
            }
        }
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