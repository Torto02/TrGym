package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise
import java.util.Collections

class WorkoutExercisesAdapter(
    private val onExerciseClick: (WorkoutExercise) -> Unit,
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onSetCompleted: (String) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit,
    private val onSaveChanges: () -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(DiffCallback) {

    private var isEditMode = false
    private var isWorkoutActive = false
    private val exerciseAdapters = mutableMapOf<String, ExerciseSetsAdapter>()
    private val pendingChanges = mutableMapOf<String, ExerciseSet>()
    private var parentRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        parentRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (parentRecyclerView === recyclerView) parentRecyclerView = null
    }

    fun setWorkoutActive(workoutActive: Boolean) {
        isWorkoutActive = workoutActive
        // Propaga lo stato (solo influenza la UI dei set, non il salvataggio)
        exerciseAdapters.values.forEach { it.setWorkoutMode(workoutActive) }
    }

    fun setEditMode(editMode: Boolean) {
        val wasInEditMode = isEditMode
        isEditMode = editMode

        // Propaga la modalità edit anche ai sotto–adapter
        exerciseAdapters.values.forEach { it.setEditMode(isEditMode) }

        if (wasInEditMode && !editMode) {
            commitFocusedEditor()
            parentRecyclerView?.post { saveAllPendingChanges() }
        }

        notifyDataSetChanged()
    }

    private fun commitFocusedEditor() {
        parentRecyclerView?.findFocus()?.clearFocus()
    }

    private fun saveAllPendingChanges() {
        if (pendingChanges.isEmpty()) {
            onSaveChanges()
            return
        }
        pendingChanges.values.forEach { onSetChanged(it) }
        pendingChanges.clear()
        onSaveChanges()
    }

    // Questo metodo viene richiamato dal ViewHolder quando crea l'adapter dei set
    private fun obtainSetsAdapter(exerciseId: String): ExerciseSetsAdapter {
        return exerciseAdapters.getOrPut(exerciseId) {
            ExerciseSetsAdapter(
                isWorkoutMode = isWorkoutActive,
                isEditMode = isEditMode,
                onSetChanged = { set ->
                    // Registro sempre la modifica se siamo in edit mode
                    if (isEditMode) pendingChanges[set.id] = set
                },
                onSetCompleted = { setId -> onSetCompleted(setId) },
                onDeleteSet = { set -> onDeleteSet(set) }
            )
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        Collections.swap(currentList, fromPosition, toPosition)
        submitList(currentList)
    }

    fun cleanup() {
        // exerciseAdapters.values.forEach { it.cleanup() }
        exerciseAdapters.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Aggiunta del metodo per payload binding
    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Aggiornamento parziale - non fare rebind completo
            holder.updatePartial(getItem(position))
        }
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val exerciseCard: MaterialCardView = itemView.findViewById(R.id.exerciseCard)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val exerciseStatusIcon: ImageView = itemView.findViewById(R.id.exerciseStatusIcon)
        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
        private val restTimerCard: MaterialCardView = itemView.findViewById(R.id.restTimerCard)
        private val restTimerText: TextView = itemView.findViewById(R.id.restTimerText)
        private val skipRestButton: ImageView = itemView.findViewById(R.id.skipRestButton)

        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets
            exerciseName.text = exercise.name
            exerciseInfo.text = "${sets.size} serie"
            setupUIForMode(exercise, sets)
            setupSetsRecyclerView(exercise.id, sets)
            setupClickListeners(exercise, sets)
        }

        fun updatePartial(exerciseDetail: WorkoutExerciseDetail) {
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets
            exerciseInfo.text = "${sets.size} serie"
            if (isWorkoutActive) {
                val allSetsCompleted = sets.all { it.reps != null && it.weightKg != null }
                if (allSetsCompleted && sets.isNotEmpty()) {
                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.primary))
                    exerciseCard.strokeColor = itemView.context.getColor(R.color.primary)
                } else {
                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.ios_secondaryLabel))
                    exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
                }
            }
            exerciseAdapters[exercise.id]?.let { adapter ->
                adapter.setEditMode(isEditMode)
                adapter.updateSetsPartial(sets)
            }
        }

        private fun setupUIForMode(exercise: WorkoutExercise, sets: List<ExerciseSet>) {
            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE

            if (isWorkoutActive) {
                exerciseStatusIcon.visibility = View.VISIBLE
                val allSetsCompleted = sets.all { it.reps != null && it.weightKg != null }
                if (allSetsCompleted && sets.isNotEmpty()) {
                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.primary))
                    exerciseCard.strokeColor = itemView.context.getColor(R.color.primary)
                } else {
                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.ios_secondaryLabel))
                    exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
                }
            } else {
                exerciseStatusIcon.visibility = View.GONE
                exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
            }

            if (sets.isEmpty()) {
                setsHeaderLayout.visibility = View.GONE
                setsRecyclerView.visibility = View.GONE
            } else {
                setsHeaderLayout.visibility = View.VISIBLE
                setsRecyclerView.visibility = View.VISIBLE
            }

            addSetButton.visibility = View.VISIBLE
            restTimerCard.visibility = View.GONE
        }

        private fun setupSetsRecyclerView(exerciseId: String, sets: List<ExerciseSet>) {
            val adapter = exerciseAdapters.getOrPut(exerciseId) {
                ExerciseSetsAdapter(
                    isWorkoutMode = isWorkoutActive,
                    isEditMode = isEditMode,
                    onSetChanged = { set ->
                        // Sempre batching (anche in workout) per coerenza
                        pendingChanges[set.id] = set
                    },
                    onSetCompleted = onSetCompleted,
                    onDeleteSet = onDeleteSet
                )
            }
            adapter.setWorkoutMode(isWorkoutActive)
            adapter.setEditMode(isEditMode)
            adapter.submitList(sets)
            setsRecyclerView.apply {
                if (this.adapter == null) {
                    this.adapter = adapter
                    layoutManager = LinearLayoutManager(context)
                    isNestedScrollingEnabled = false
                    setHasFixedSize(true)
                }
            }
        }

        private fun setupClickListeners(exercise: WorkoutExercise, sets: List<ExerciseSet>) {
            exerciseName.setOnClickListener { onExerciseClick(exercise) }
            exerciseMenu.setOnClickListener { showExerciseMenu(exercise) }
            addSetButton.setOnClickListener { onAddSetClick(exercise.id) }
            skipRestButton.setOnClickListener {
                restTimerCard.visibility = View.GONE
            }
        }

        private fun showExerciseMenu(exercise: WorkoutExercise) {
            // TODO menu
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
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
            return oldItem == newItem
        }

        // Aggiunta del payload per aggiornamenti parziali
        override fun getChangePayload(
            oldItem: WorkoutExerciseDetail,
            newItem: WorkoutExerciseDetail
        ): Any? {
            return if (oldItem.exercise == newItem.exercise) {
                "SETS_CHANGED" // Payload per indicare che sono cambiati solo i set
            } else {
                null
            }
        }
    }
}

// Adapter separato per i set di ogni esercizio
class ExerciseSetsAdapter(
    private var isWorkoutMode: Boolean,
    private var isEditMode: Boolean,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onSetCompleted: (String) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<ExerciseSet, ExerciseSetsAdapter.SetViewHolder>(SetDiffCallback) {

    fun setWorkoutMode(workoutMode: Boolean) {
        isWorkoutMode = workoutMode
        notifyDataSetChanged()
    }
    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }
    fun updateSetsPartial(newSets: List<ExerciseSet>) = submitList(newSets)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_set, parent, false)
        return SetViewHolder(view)
    }
    override fun onBindViewHolder(holder: SetViewHolder, position: Int) = holder.bind(getItem(position))
    override fun onBindViewHolder(holder: SetViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads)
        else holder.updatePartial(getItem(position))
    }

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val setRowLayout: View = itemView.findViewById(R.id.setRowLayout)
        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
        private val weightInput: com.google.android.material.textfield.TextInputEditText = itemView.findViewById(R.id.weightInput)
        private val repsInput: com.google.android.material.textfield.TextInputEditText = itemView.findViewById(R.id.repsInput)
        private val setCompletedCheckbox: com.google.android.material.checkbox.MaterialCheckBox = itemView.findViewById(R.id.setCompletedCheckbox)
        private val setMenu: ImageView = itemView.findViewById(R.id.setMenu)

        private var currentSet: ExerciseSet? = null
        private var isBinding = false

        fun bind(set: ExerciseSet) {
            isBinding = true
            currentSet = set
            setNumber.text = set.setNumber.toString()
            weightInput.setText(set.weightKg?.toString() ?: "")
            repsInput.setText(set.reps?.toString() ?: "")
            val canEdit = isEditMode        // RIMOSSO blocco !isWorkoutMode
            weightInput.isEnabled = canEdit
            repsInput.isEnabled = canEdit
            updateUI(set)
            if (canEdit) setupInputListeners()
            isBinding = false
        }

        fun updatePartial(set: ExerciseSet) {
            currentSet = set
            if (!isEditMode) {
                isBinding = true
                weightInput.setText(set.weightKg?.toString() ?: "")
                repsInput.setText(set.reps?.toString() ?: "")
                isBinding = false
            }
            updateUI(set)
        }

        private fun updateUI(set: ExerciseSet) {
            if (isWorkoutMode) {
                setCompletedCheckbox.visibility = View.VISIBLE
                val completed = set.weightKg != null && set.reps != null
                setCompletedCheckbox.setOnCheckedChangeListener(null)
                setCompletedCheckbox.isChecked = completed
                if (completed) {
                    setRowLayout.setBackgroundColor(itemView.context.getColor(R.color.gray))
                } else setRowLayout.background = null
                setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked && !isBinding && currentSet != null) {
                        // Se l’utente ha messo la spunta ma i valori sono stati appena editati, li prendiamo ora
                        commitInputsToPending()
                        onSetCompleted(currentSet!!.id)
                    }
                }
            } else {
                setCompletedCheckbox.visibility = View.GONE
                setRowLayout.background = null
            }
            val alpha = if (isEditMode) 1f else 0.7f
            weightInput.alpha = alpha
            repsInput.alpha = alpha
            setMenu.visibility = if (isEditMode) View.VISIBLE else View.GONE
        }

        private fun setupInputListeners() {
            // Rimuovi vecchi (per sicurezza)
            weightInput.onFocusChangeListener = null
            repsInput.onFocusChangeListener = null

            weightInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isBinding) {
                    commitInputsToPending()
                }
            }
            repsInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isBinding) {
                    commitInputsToPending()
                }
            }
        }

        private fun commitInputsToPending() {
            val base = currentSet ?: return
            val weight = weightInput.text.toString().trim().toDoubleOrNull()
            val reps = repsInput.text.toString().trim().toIntOrNull()
            if (weight != base.weightKg || reps != base.reps) {
                val updated = base.copy(
                    weightKg = weight,
                    reps = reps,
                    updatedAt = System.currentTimeMillis()
                )
                currentSet = updated
                onSetChanged(updated)
            }
        }

        private fun showSetMenu(set: ExerciseSet) {
            // TODO: Implementare menu popup con opzioni:
            // - Duplica serie
            // - Elimina serie
            // - Inserisci serie sopra/sotto
        }

        fun cleanup() {
            // Cleanup semplificato
        }
    }

    object SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: ExerciseSet, newItem: ExerciseSet): Any? {
            return if (oldItem.id == newItem.id &&
                (oldItem.weightKg != newItem.weightKg || oldItem.reps != newItem.reps)) {
                "VALUE_CHANGED"
            } else {
                null
            }
        }
    }
}