package com.torto.trgym.adapters

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import coil.load
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
    
    // Flag to prevent unnecessary refreshes during user interactions
    private var suppressUpdates = false

    fun updateCompletedSets(setCompletionMap: Map<String, Boolean>) {
        if (suppressUpdates) return // Skip updates during user interactions
        
        val oldCompletedSets = completedSets.toMap()
        completedSets.clear()
        completedSets.putAll(setCompletionMap)
        
        // Only update items where completion status actually changed
        if (oldCompletedSets != completedSets) {
            notifyDataSetChanged() // For exercises adapter, it's safer to refresh all since it affects nested adapters
        }
    }
    
    fun updateCompletedSetDirectly(sessionSetId: String, isCompleted: Boolean) {
        // Update local state without triggering refresh
        completedSets[sessionSetId] = isCompleted
        
        // Update the specific sets adapter if needed
        for (i in 0 until itemCount) {
            val holder = currentList[i]
            // Find the exercise that contains this set
            if (holder.sets.any { set -> 
                val mappedId = setIdMapping[set.id] ?: set.id
                mappedId == sessionSetId 
            }) {
                notifyItemChanged(i, "completion_update")
                break
            }
        }
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
    
    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "completion_update") {
            // Only update completion-related UI elements
            holder.updateCompletionInfo(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val exercisePreview: ImageView = itemView.findViewById(R.id.exercisePreviewIcon)

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



            val defaultRes = R.drawable.ic_fitness_center

            val imagePath = exercise.imagePath
            Log.d("imagePath", "Image path: $imagePath")
            if (!imagePath.isNullOrBlank()) {
                val assetUri = "file:///android_asset/images/$imagePath"
                exercisePreview.load(assetUri) {
                    crossfade(true)
                    error(defaultRes)
                    placeholder(defaultRes)
                }
            } else {
                exercisePreview.load(defaultRes) {
                    crossfade(true)
                }
            }

            // Show/hide sets header based on whether we have sets
            setsHeaderLayout.visibility = if (sets.isNotEmpty()) View.VISIBLE else View.GONE

            // Update sets adapter
            if (setsAdapter == null) {
                setsAdapter = ActiveWorkoutSetsAdapter(
                    onSetCompletionChanged = { setId, isCompleted ->
                        // The setId passed here is the original set ID, but we need to use
                        // the session set ID for completion tracking
                        val sessionSetId = setIdMapping[setId] ?: setId
                        
                        // Update completion state directly in the parent adapter
                        this@ActiveWorkoutExercisesAdapter.updateCompletedSetDirectly(sessionSetId, isCompleted)
                        
                        // Notify the ViewModel but suppress flow updates temporarily
                        suppressUpdates = true
                        onSetCompletionChanged(sessionSetId, isCompleted)
                        // Reset the flag after a short delay
                        setsRecyclerView.post { suppressUpdates = false }
                    }
                )
                setsRecyclerView.adapter = setsAdapter
            }

            setsAdapter?.submitList(sets) {
                // Update completion status only after the list is updated
                setsAdapter?.updateCompletedSets(completedSets, setIdMapping)
            }
        }
        
        fun updateCompletionInfo(exerciseDetail: WorkoutExerciseDetail) {
            val sets = exerciseDetail.sets
            
            // Calculate exercise completion status
            val totalSets = sets.size
            val completedSetsCount = sets.count { set ->
                val sessionSetId = setIdMapping[set.id]
                sessionSetId != null && completedSets[sessionSetId] == true
            }

            // Update exercise info text
            exerciseInfo.text = "$completedSetsCount/$totalSets serie"
            
            // Update the sets adapter with new completion state
            setsAdapter?.updateCompletedSets(completedSets, setIdMapping)
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
