package com.torto.trgym.adapters

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
import com.google.android.material.button.MaterialButton
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise

class WorkoutExercisesAdapter(
    private val onExerciseClick: (WorkoutExercise) -> Unit,
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    private var isEditMode = false

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            notifyDataSetChanged()
        }
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
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
        private val exerciseInfoLayout: View = itemView.findViewById(R.id.exerciseInfoLayout)

        private var setsAdapter: ExerciseSetsAdapter? = null
        private var currentExercise: WorkoutExercise? = null

        init {
            setupRecyclerView()
            setupClickListeners()
        }



        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            currentExercise = exerciseDetail.exercise
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets

            // Update UI based on edit mode
            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE
            exerciseMenu.visibility = if (isEditMode) View.VISIBLE else View.GONE
            addSetButton.visibility = if (isEditMode) View.VISIBLE else View.GONE

            // Set exercise info
            exerciseName.text = exercise.name
            
            // Add an indicator for edit mode
            if (isEditMode) {
                exerciseName.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary))
            } else {
                exerciseName.setTextColor(ContextCompat.getColor(itemView.context, R.color.ios_label))
            }

            // Calculate exercise info
            val setsCount = sets.size
            val exerciseInfoText = "$setsCount serie"
            exerciseInfo.text = exerciseInfoText

            // Show/hide sets header based on whether we have sets
            setsHeaderLayout.visibility = if (sets.isNotEmpty()) View.VISIBLE else View.GONE

            // Update sets adapter
            if (setsAdapter == null) {
                setsAdapter = ExerciseSetsAdapter(
                    onSetChanged = onSetChanged,
                    onDeleteSet = onDeleteSet
                )
                setsRecyclerView.adapter = setsAdapter
            }
            setsAdapter?.setEditMode(isEditMode)
            setsAdapter?.submitList(sets)
        }

        private fun setupRecyclerView() {
            setsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(false)
                isNestedScrollingEnabled = false
            }
        }

        private fun setupClickListeners() {

            exerciseName.setOnClickListener {
                    currentExercise?.let { exercise ->
                        Log.d("ExerciseViewHolder", "Exercise clicked: $exercise")
                        onExerciseClick(exercise)  // Passa l'intero oggetto
                    }
                }


            exerciseMenu.setOnClickListener {
                currentExercise?.let { exercise ->
                    onDeleteExercise(exercise)
                }
            }



            addSetButton.setOnClickListener {
                currentExercise?.let { exercise ->
                    onAddSetClick(exercise.id)
                }
            }


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
