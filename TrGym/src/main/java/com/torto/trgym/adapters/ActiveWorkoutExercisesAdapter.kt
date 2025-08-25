package com.torto.trgym.adapters
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail


class ActiveWorkoutExercisesAdapter(
    private val onSetCompletionChanged: (String, Boolean) -> Unit,
) : ListAdapter<WorkoutExerciseDetail, ActiveWorkoutExercisesAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {
    private val completedSets = mutableMapOf<String, Boolean>()
    private val setIdMapping = mutableMapOf<String, String>()
    private var suppressUpdates = false

    fun updateCompletedSets(setCompletionMap: Map<String, Boolean>) {
        if (suppressUpdates) return // Skip updates during user interactions
        
        val oldCompletedSets = completedSets.toMap()
        completedSets.clear()
        completedSets.putAll(setCompletionMap)

        if (oldCompletedSets != completedSets) {
            notifyDataSetChanged()
        }
    }
    
    fun updateCompletedSetDirectly(sessionSetId: String, isCompleted: Boolean) {
        completedSets[sessionSetId] = isCompleted

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
//        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
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

            val totalSets = sets.size
            val completedSetsCount = sets.count { set ->
                val sessionSetId = setIdMapping[set.id]
                sessionSetId != null && completedSets[sessionSetId] == true
            }

            exerciseInfo.text = "$completedSetsCount/$totalSets serie"


            val defaultRes = R.drawable.ic_fitness_center

            val imagePath = exercise.imagePath
            Log.d("imagePath", "Image path: $exercise")
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

//            setsHeaderLayout.visibility = if (sets.isNotEmpty()) View.VISIBLE else View.GONE

            if (setsAdapter == null) {
                setsAdapter = ActiveWorkoutSetsAdapter(
                    onSetCompletionChanged = { setId, isCompleted ->
                        val sessionSetId = setIdMapping[setId] ?: setId
                        

                        this@ActiveWorkoutExercisesAdapter.updateCompletedSetDirectly(sessionSetId, isCompleted)

                        suppressUpdates = true
                        onSetCompletionChanged(sessionSetId, isCompleted)
                        setsRecyclerView.post { suppressUpdates = false }
                    }
                )
                setsRecyclerView.adapter = setsAdapter
            }

            setsAdapter?.submitList(sets) {
                setsAdapter?.updateCompletedSets(completedSets, setIdMapping)
            }
        }
        
        fun updateCompletionInfo(exerciseDetail: WorkoutExerciseDetail) {
            val sets = exerciseDetail.sets
            val totalSets = sets.size
            val completedSetsCount = sets.count { set ->
                val sessionSetId = setIdMapping[set.id]
                sessionSetId != null && completedSets[sessionSetId] == true
            }

            exerciseInfo.text = "$completedSetsCount/$totalSets serie"

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
