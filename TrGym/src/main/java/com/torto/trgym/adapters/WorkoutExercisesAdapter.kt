package com.torto.trgym.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise
import coil.load

class WorkoutExercisesAdapter(
    private val onExerciseClick: (WorkoutExercise) -> Unit,
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit,
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    private var isEditMode = false
    private var itemTouchHelper: ItemTouchHelper? = null

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            notifyDataSetChanged()
        }
    }

    fun setItemTouchHelper(itemTouchHelper: ItemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper
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
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update - only update sets
            val exerciseDetail = getItem(position)
            for (payload in payloads) {
                when (payload) {
                    "sets_changed" -> {
                        holder.updateSets(exerciseDetail.sets)
                    }
                }
            }
        }
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
        private val exercisePreview: ImageView = itemView.findViewById(R.id.exercisePreviewIcon)

        private var setsAdapter: ExerciseSetsAdapter? = null
        private var currentExercise: WorkoutExercise? = null

        init {
            setupRecyclerView()
            setupClickListeners()
            setupDragHandle()
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

            // Carica l'immagine dell'esercizio con Coil
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

            // Add an indicator for edit mode
            if (isEditMode) {
                exerciseName.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary))
                // Add a subtle background to indicate draggable state

            } else {
                exerciseName.setTextColor(ContextCompat.getColor(itemView.context, R.color.ios_label))

            }

            // Calculate exercise info
            val setsCount = sets.size
            val exerciseInfoText = "$setsCount set"
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

        fun updateSets(sets: List<ExerciseSet>) {
            // Update exercise info
            val setsCount = sets.size
            val exerciseInfoText = "$setsCount set"
            exerciseInfo.text = exerciseInfoText

            // Show/hide sets header based on whether we have sets
            setsHeaderLayout.visibility = if (sets.isNotEmpty()) View.VISIBLE else View.GONE

            // Update sets adapter
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
                if (!isEditMode) { // Solo se non siamo in edit mode
                    currentExercise?.let { exercise ->
                        Log.d("ExerciseViewHolder", "Exercise clicked: $exercise")
                        onExerciseClick(exercise)
                    }
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

        private fun setupDragHandle() {
            dragHandle.setOnTouchListener { _, event ->
                if (isEditMode && event.action == MotionEvent.ACTION_DOWN) {
                    // Inizia il drag
                    itemTouchHelper?.startDrag(this@ExerciseViewHolder)

                    // Feedback visivo immediato
                    dragHandle.alpha = 0.7f

                    // Ripristina l'alpha dopo un breve delay
                    dragHandle.postDelayed({
                        dragHandle.alpha = 1.0f
                    }, 100)

                    true
                } else {
                    false
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
                        .all { (old, new) -> old == new }
        }

        override fun getChangePayload(
            oldItem: WorkoutExerciseDetail,
            newItem: WorkoutExerciseDetail
        ): Any? {
            if (oldItem.exercise.id != newItem.exercise.id) return null

            // Se solo i sets sono cambiati (non l'esercizio stesso)
            if (oldItem.exercise == newItem.exercise && oldItem.sets != newItem.sets) {
                return "sets_changed"
            }

            return null
        }
    }
}