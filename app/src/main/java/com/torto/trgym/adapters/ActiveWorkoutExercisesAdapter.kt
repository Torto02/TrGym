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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet

class ActiveWorkoutExercisesAdapter(
    private val onSetCompletionChanged: (String, Boolean) -> Unit,
    private val onSetValueChangedSimple: ((String, Double?, Int?) -> Unit)? = null,
    private val onExerciseMenuClicked: ((String) -> Unit)? = null,
    private val onSetMenuClicked: ((String) -> Unit)? = null,
    private val onAddSetClicked: ((String) -> Unit)? = null
) : ListAdapter<WorkoutExerciseDetail, ActiveWorkoutExercisesAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    // Map to track the completion status of sets
    private val completedSets = mutableMapOf<String, Boolean>()

    // Map to associate original ExerciseSet IDs with WorkoutSessionSet IDs
    private val setIdMapping = mutableMapOf<String, String>()

    fun updateCompletedSets(setCompletionMap: Map<String, Boolean>) {
        completedSets.clear()
        completedSets.putAll(setCompletionMap)
        notifyDataSetChanged()
    }

    fun updateSetIdMapping(mapping: Map<String, String>) {
        setIdMapping.clear()
        setIdMapping.putAll(mapping)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
        private val exerciseStatusIcon: ImageView = itemView.findViewById(R.id.exerciseStatusIcon)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

        private var setsAdapter: ActiveWorkoutSetsAdapter? = null

        init {
            setupRecyclerView()
        }

        private fun setupRecyclerView() {
            setsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(false)
                isNestedScrollingEnabled = false
            }
        }

        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets

            // Nascondi sempre gli elementi di edit mode
            exerciseMenu.visibility = View.GONE
            addSetButton.visibility = View.GONE
            dragHandle.visibility = View.GONE
            exerciseStatusIcon.visibility = View.VISIBLE

            exerciseName.text = exercise.name

            // Calculate exercise completion status
            val totalSets = sets.size
            val completedSetsCount = sets.count { set ->
                val sessionSetId = setIdMapping[set.id]
                sessionSetId != null && completedSets[sessionSetId] == true
            }

            // Update exercise info text
            exerciseInfo.text = "$completedSetsCount/$totalSets serie"

            // Update status icon based on completion
            val isExerciseCompleted = totalSets > 0 && completedSetsCount == totalSets
            exerciseStatusIcon.setImageResource(
                when {
                    isExerciseCompleted -> R.drawable.ic_check_circle
                    completedSetsCount > 0 -> R.drawable.ic_partial_complete
                    else -> R.drawable.ic_play
                }
            )

            // Show/hide sets header based on whether we have sets
            setsHeaderLayout.visibility = if (sets.isNotEmpty()) View.VISIBLE else View.GONE

            // Update sets adapter
            if (setsAdapter == null) {
                setsAdapter = ActiveWorkoutSetsAdapter(
                    onSetCompletionChanged = { setId, isCompleted ->
                        // The ID is already the session set ID when in session mode
                        onSetCompletionChanged(setId, isCompleted)
                    },
                    onSetValueChanged = { setId, weight, reps ->
                        // Legacy callback - not used in session mode
                    },
                    onSetMenuClicked = { setId ->
                        // The ID is already the session set ID when in session mode
                        onSetMenuClicked?.invoke(setId)
                    },
                    onSetValueChangedSimple = { setId, weight, reps ->
                        // Direct callback for session mode
                        onSetValueChangedSimple?.invoke(setId, weight, reps)
                    }
                )
                setsRecyclerView.adapter = setsAdapter
            }

            setsAdapter?.updateCompletedSets(completedSets, setIdMapping)
            setsAdapter?.submitList(sets)
        }
    }

    class ExerciseDiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
        override fun areItemsTheSame(
            oldItem: WorkoutExerciseDetail,
            newItem: WorkoutExerciseDetail
        ): Boolean {
            return oldItem.exercise.id == newItem.exercise.id
        }

        override fun areContentsTheSame(
            oldItem: WorkoutExerciseDetail,
            newItem: WorkoutExerciseDetail
        ): Boolean {
            return oldItem.exercise == newItem.exercise &&
                    oldItem.sets.size == newItem.sets.size &&
                    oldItem.sets.zip(newItem.sets)
                        .all { (old, new) -> old.id == new.id && old == new }
        }
    }
}

class ActiveWorkoutSetsAdapter(
    private val onSetCompletionChanged: (String, Boolean) -> Unit,
    private val onSetValueChanged: (String, Double?, Int?) -> Unit,
    private val onSetMenuClicked: ((String) -> Unit)? = null,
    private val onSetValueChangedSimple: ((String, Double?, Int?) -> Unit)? = null
) : ListAdapter<ExerciseSet, ActiveWorkoutSetsAdapter.SetViewHolder>(SetDiffCallback()) {

    private val completedSets = mutableMapOf<String, Boolean>()
    private val setIdMapping = mutableMapOf<String, String>()

    fun updateCompletedSets(setCompletionMap: Map<String, Boolean>, mapping: Map<String, String>) {
        completedSets.clear()
        completedSets.putAll(setCompletionMap)

        setIdMapping.clear()
        setIdMapping.putAll(mapping)

        notifyDataSetChanged()
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
                    currentSet?.let {
                        onSetCompletionChanged(it.id, isChecked)
                    }
                }
            }

            weightInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!isBindingData) {
                        updateSetValues()
                    }
                }
            })

            repsInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!isBindingData) {
                        updateSetValues()
                    }
                }
            })
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
            val sessionSetId = setIdMapping[set.id]
            val isCompleted = sessionSetId?.let { completedSets[it] } ?: false
            setCompletedCheckbox.isChecked = isCompleted

            // Highlight the entire row if completed
            itemView.background.setTint(
                if (isCompleted) 
                    ContextCompat.getColor(itemView.context, R.color.completed_set_background)
                else 
                    ContextCompat.getColor(itemView.context, R.color.transparent)
            )

            // Disabilita sempre gli input (non siamo in edit mode)
            weightInput.isEnabled = false
            repsInput.isEnabled = false

            isBindingData = false
        }

        private fun updateSetValues() {
            currentSet?.let {
                val weight = weightInput.text.toString().toDoubleOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                // Use the simple callback for direct session set updates
                onSetValueChangedSimple?.invoke(it.id, weight, reps) ?: onSetValueChanged(it.id, weight, reps)
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
