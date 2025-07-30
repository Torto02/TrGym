package com.torto.trgym.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.dto.WorkoutExerciseDetail

class WorkoutExercisesAdapter(
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ViewHolder(view, onAddSetClick, onSetChanged, onDeleteExercise)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onAddSetClick: (String) -> Unit,
        private val onSetChanged: (ExerciseSet) -> Unit,
        private val onDeleteExercise: (WorkoutExercise) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val expandCollapseIcon: ImageView = itemView.findViewById(R.id.expandCollapseIcon)
        private val setsContainer: LinearLayout = itemView.findViewById(R.id.setsContainer)
        private val dynamicSetsContainer: LinearLayout = itemView.findViewById(R.id.dynamicSetsContainer)
        private val addSetBtn: MaterialButton = itemView.findViewById(R.id.addSetBtn)

        private var isExpanded = false
        private var currentExerciseDetail: WorkoutExerciseDetail? = null

        init {
            // Click listener per espandere/comprimere
            itemView.setOnClickListener {
                toggleExpansion()
            }

            expandCollapseIcon.setOnClickListener {
                toggleExpansion()
            }
        }

        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            currentExerciseDetail = exerciseDetail

            exerciseName.text = exerciseDetail.exercise.name

            // Setup del pulsante aggiungi set
            addSetBtn.setOnClickListener {
                onAddSetClick(exerciseDetail.exercise.id)
            }

            // Long click per eliminare esercizio
            itemView.setOnLongClickListener {
                onDeleteExercise(exerciseDetail.exercise)
                true
            }

            // Popola i set
//            populateSets(exerciseDetail.sets)
        }

        private fun toggleExpansion() {
            isExpanded = !isExpanded

            if (isExpanded) {
                setsContainer.visibility = View.VISIBLE
                expandCollapseIcon.setImageResource(R.drawable.arrow_back)
            } else {
                setsContainer.visibility = View.GONE
                expandCollapseIcon.setImageResource(R.drawable.ic_email)
            }
        }

        private fun populateSets(sets: List<ExerciseSet>) {
            dynamicSetsContainer.removeAllViews()

            sets.forEach { set ->
                val setView = createSetView(set)
                dynamicSetsContainer.addView(setView)
            }
        }

        private fun createSetView(set: ExerciseSet): View {
            val setView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.item_exercise_set, dynamicSetsContainer, false)

            val setNumber = setView.findViewById<TextView>(R.id.setNumber)
            val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
            val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)

            setNumber.text = set.setNumber.toString()
            weightEditText.setText(set.weightKg?.toString() ?: "")
            repsEditText.setText(set.reps?.toString() ?: "")

            // TextWatcher per il peso
            weightEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val weight = s.toString().toDoubleOrNull()
                    val updatedSet = set.copy(weightKg = weight)
                    onSetChanged(updatedSet)
                }
            })

            // TextWatcher per le ripetizioni
            repsEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val reps = s.toString().toIntOrNull()
                    val updatedSet = set.copy(reps = reps)
                    onSetChanged(updatedSet)
                }
            })

            return setView
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
        override fun areItemsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
            return oldItem.exercise.id == newItem.exercise.id
        }

        override fun areContentsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
            return oldItem == newItem
        }
    }
}