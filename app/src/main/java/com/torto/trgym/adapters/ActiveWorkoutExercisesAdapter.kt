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
        // No need to call notifyDataSetChanged() here as it will be called by updateCompletedSets
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
                        // The setId passed here is the original set ID, but we need to use
                        // the session set ID for completion tracking
                        val sessionSetId = setIdMapping[setId] ?: setId
                        onSetCompletionChanged(sessionSetId, isCompleted)
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
