//
//package com.torto.trgym.adapters
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.google.android.material.button.MaterialButton
//import com.google.android.material.card.MaterialCardView
//import com.torto.trgym.R
//import com.torto.trgym.database.dto.WorkoutExerciseDetail
//import com.torto.trgym.database.entities.ExerciseSet
//import com.torto.trgym.database.entities.WorkoutExercise
//import java.util.Collections
//
//class WorkoutExercisesAdapter(
//    private val onExerciseClick: (WorkoutExercise) -> Unit,
//    private val onAddSetClick: (String) -> Unit,
//    private val onSetChanged: (ExerciseSet) -> Unit,
//    private val onSetCompleted: (String) -> Unit,
//    private val onDeleteExercise: (WorkoutExercise) -> Unit,
//    private val onDeleteSet: (ExerciseSet) -> Unit
//) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(DiffCallback) {
//
//    init {
//        // Abilitiamo gli stable IDs per evitare il ricrearsi inutile delle view e perdita del focus
//        setHasStableIds(true)
//    }
//
//    private var isEditMode = false
//    private var isWorkoutActive = false
//    val exerciseAdapters = mutableMapOf<String, ExerciseSetsAdapter>()
//
//    fun setEditMode(editMode: Boolean) {
//        isEditMode = editMode
//        notifyDataSetChanged()
//    }
//
//    fun setWorkoutActive(workoutActive: Boolean) {
//        isWorkoutActive = workoutActive
//        notifyDataSetChanged()
//    }
//
//    fun moveItem(fromPosition: Int, toPosition: Int) {
//        val currentList = currentList.toMutableList()
//        Collections.swap(currentList, fromPosition, toPosition)
//        submitList(currentList)
//    }
//
//    fun cleanup() {
//        exerciseAdapters.clear()
//    }
//
//    fun updateSessionSetCompletion(exerciseId: String, setId: String, isCompleted: Boolean) {
//        exerciseAdapters[exerciseId]?.updateSetCompletionStatus(setId, isCompleted)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_workout_exercise, parent, false)
//        return ExerciseViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
//        holder.bind(getItem(position))
//    }
//
//    override fun getItemId(position: Int): Long {
//        // Usa l'id dell'esercizio come stable id
//        return getItem(position).exercise.id.hashCode().toLong()
//    }
//
//    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//
//        private val exerciseCard: MaterialCardView = itemView.findViewById(R.id.exerciseCard)
//        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
//        private val exerciseStatusIcon: ImageView = itemView.findViewById(R.id.exerciseStatusIcon)
//        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
//        private val exerciseInfo: TextView = itemView.findViewById(R.id.exerciseInfo)
//        private val exerciseMenu: ImageView = itemView.findViewById(R.id.exerciseMenu)
//        private val setsHeaderLayout: View = itemView.findViewById(R.id.setsHeaderLayout)
//        private val setsRecyclerView: RecyclerView = itemView.findViewById(R.id.setsRecyclerView)
//        private val addSetButton: MaterialButton = itemView.findViewById(R.id.addSetButton)
//        private val restTimerCard: MaterialCardView = itemView.findViewById(R.id.restTimerCard)
//        private val restTimerText: TextView = itemView.findViewById(R.id.restTimerText)
//        private val skipRestButton: ImageView = itemView.findViewById(R.id.skipRestButton)
//
//        fun bind(exerciseDetail: WorkoutExerciseDetail) {
//            val exercise = exerciseDetail.exercise
//            val sets = exerciseDetail.sets
//
//            // Setup base info
//            exerciseName.text = exercise.name
//            exerciseInfo.text = "${sets.size} serie"
//
//            // Gestione visibilità elementi UI
//            setupUIForMode(exercise, sets)
//
//            // Setup RecyclerView per i set
//            setupSetsRecyclerView(exercise.id, sets)
//
//            // Click listeners
//            setupClickListeners(exercise)
//        }
//
//    private fun setupUIForMode(@Suppress("UNUSED_PARAMETER") exercise: WorkoutExercise, sets: List<ExerciseSet>) {
//            // Drag handle visibile solo in modalità edit
//            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE
//
//            // Status icon visibile solo durante allenamento
//            if (isWorkoutActive) {
//                exerciseStatusIcon.visibility = View.VISIBLE
//                // During workout, we need to check completion status from the session data
//                // This will be updated via updateSetCompletionStatus method
//                val allSetsCompleted = false // Will be managed by the parent component
//                if (allSetsCompleted && sets.isNotEmpty()) {
//                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
//                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.primary))
//                    exerciseCard.strokeColor = itemView.context.getColor(R.color.primary)
//                } else {
//                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
//                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.ios_secondaryLabel))
//                    exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
//                }
//            } else {
//                exerciseStatusIcon.visibility = View.GONE
//                exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
//            }
//
//            // Header e contenuto set
//            if (sets.isEmpty()) {
//                setsHeaderLayout.visibility = View.GONE
//                setsRecyclerView.visibility = View.GONE
//            } else {
//                setsHeaderLayout.visibility = View.VISIBLE
//                setsRecyclerView.visibility = View.VISIBLE
//            }
//
//            // Pulsante aggiungi serie sempre visibile
//            addSetButton.visibility = View.VISIBLE
//
//            // Rest timer visibile solo durante allenamento e se necessario
//            restTimerCard.visibility = View.GONE // Sarà gestito dal ViewModel
//        }
//
//        private fun setupSetsRecyclerView(exerciseId: String, sets: List<ExerciseSet>) {
//            val adapter = exerciseAdapters.getOrPut(exerciseId) {
//                ExerciseSetsAdapter(
//                    isWorkoutMode = isWorkoutActive,
//                    onSetChanged = onSetChanged,
//                    onSetCompleted = onSetCompleted,
//                    onDeleteSet = onDeleteSet
//                )
//            }
//
//            adapter.updateWorkoutModeIfChanged(isWorkoutActive)
//            // Usa submitList con una nuova lista copia per far lavorare correttamente DiffUtil senza riassegnare adapter
//            adapter.submitList(sets.toList())
//
//            if (setsRecyclerView.adapter !== adapter) {
//                setsRecyclerView.adapter = adapter
//                if (setsRecyclerView.layoutManager == null) {
//                    setsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
//                }
//                setsRecyclerView.isNestedScrollingEnabled = false
//                setsRecyclerView.setHasFixedSize(true)
//            }
//        }
//
//    private fun setupClickListeners(exercise: WorkoutExercise) {
//            // Click sul nome dell'esercizio
//            exerciseName.setOnClickListener {
//                onExerciseClick(exercise)
//            }
//
//            // Menu esercizio
//            exerciseMenu.setOnClickListener {
//                showExerciseMenu(exercise)
//            }
//
//            // Aggiungi serie
//            addSetButton.setOnClickListener {
//                onAddSetClick(exercise.id)
//            }
//
//            // Skip rest timer
//            skipRestButton.setOnClickListener {
//                restTimerCard.visibility = View.GONE
//                // TODO: Notificare il ViewModel per fermare il timer
//            }
//        }
//
//        private fun showExerciseMenu(exercise: WorkoutExercise) {
//            val context = itemView.context
//            val options = arrayOf(
//                "Vedi dettagli esercizio",
//                "Modifica nome",
//                "Duplica esercizio",
//                "Elimina esercizio"
//            )
//
//            android.app.AlertDialog.Builder(context)
//                .setTitle(exercise.name)
//                .setItems(options) { _, which ->
//                    when (which) {
//                        0 -> onExerciseClick(exercise) // Vedi dettagli
//                        1 -> showEditNameDialog(exercise)
//                        2 -> duplicateExercise(exercise)
//                        3 -> onDeleteExercise(exercise)
//                    }
//                }
//                .show()
//        }
//
//        private fun showEditNameDialog(exercise: WorkoutExercise) {
//            val context = itemView.context
//            val editText = android.widget.EditText(context).apply {
//                setText(exercise.name)
//                selectAll()
//            }
//
//            android.app.AlertDialog.Builder(context)
//                .setTitle("Modifica nome esercizio")
//                .setView(editText)
//                .setPositiveButton("Salva") { _, _ ->
//                    val newName = editText.text.toString().trim()
//                    if (newName.isNotEmpty() && newName != exercise.name) {
//                        // TODO: Implementare callback per aggiornare nome
//                        // onExerciseNameChanged(exercise.copy(name = newName))
//                    }
//                }
//                .setNegativeButton("Annulla", null)
//                .show()
//        }
//
//        private fun duplicateExercise(exercise: WorkoutExercise) {
//            // TODO: Implementare callback per duplicare esercizio
//            // onDuplicateExercise(exercise)
//        }
//    }
//
//    object DiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
//        override fun areItemsTheSame(
//            oldItem: WorkoutExerciseDetail,
//            newItem: WorkoutExerciseDetail
//        ): Boolean {
//            return oldItem.exercise.id == newItem.exercise.id
//        }
//
//        override fun areContentsTheSame(
//            oldItem: WorkoutExerciseDetail,
//            newItem: WorkoutExerciseDetail
//        ): Boolean {
//            return oldItem == newItem
//        }
//    }
//}
//
//// Adapter separato per i set di ogni esercizio
//class ExerciseSetsAdapter(
//    private var isWorkoutMode: Boolean,
//    private val onSetChanged: (ExerciseSet) -> Unit,
//    private val onSetCompleted: (String) -> Unit,
//    private val onDeleteSet: (ExerciseSet) -> Unit
//) : ListAdapter<ExerciseSet, ExerciseSetsAdapter.SetViewHolder>(SetDiffCallback) {
//
//    private val sessionSetCompletionStatus = mutableMapOf<String, Boolean>()
//
//    init { setHasStableIds(true) }
//
//    fun updateWorkoutModeIfChanged(workoutMode: Boolean) {
//        if (isWorkoutMode != workoutMode) {
//            isWorkoutMode = workoutMode
//            // Aggiorna solo le view visibili
//            notifyItemRangeChanged(0, itemCount, PAYLOAD_MODE_CHANGE)
//        }
//    }
//
//    fun updateSetCompletionStatus(setId: String, isCompleted: Boolean) {
//        sessionSetCompletionStatus[setId] = isCompleted
//        // Trova posizione e aggiorna solo quella
//        val index = currentList.indexOfFirst { it.id == setId }
//        if (index != -1) notifyItemChanged(index, PAYLOAD_COMPLETION_CHANGE)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_exercise_set, parent, false)
//        return SetViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
//        holder.bind(getItem(position))
//    }
//
//    override fun onBindViewHolder(holder: SetViewHolder, position: Int, payloads: MutableList<Any>) {
//        if (payloads.isEmpty()) {
//            super.onBindViewHolder(holder, position, payloads)
//        } else {
//            val set = getItem(position)
//            holder.partialBind(set, payloads)
//        }
//    }
//
//    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()
//
//    companion object {
//        private const val PAYLOAD_MODE_CHANGE = "mode_change"
//        private const val PAYLOAD_COMPLETION_CHANGE = "completion_change"
//    }
//
//    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//
//        private val setRowLayout: View = itemView.findViewById(R.id.setRowLayout)
//        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
//        private val weightInput: com.google.android.material.textfield.TextInputEditText =
//            itemView.findViewById(R.id.weightInput)
//        private val repsInput: com.google.android.material.textfield.TextInputEditText =
//            itemView.findViewById(R.id.repsInput)
//        private val setCompletedCheckbox: com.google.android.material.checkbox.MaterialCheckBox =
//            itemView.findViewById(R.id.setCompletedCheckbox)
//        private val setMenu: ImageView = itemView.findViewById(R.id.setMenu)
//
//        fun bind(set: ExerciseSet) {
//            setNumber.text = set.setNumber.toString()
//
//            // Salva la posizione del cursore se il campo ha focus
//            val weightCursorPos = if (weightInput.hasFocus()) weightInput.selectionStart else -1
//            val repsCursorPos = if (repsInput.hasFocus()) repsInput.selectionStart else -1
//
//            // Imposta i valori degli input solo se sono cambiati per evitare focus jumping
//            val expectedWeightText = set.weightKg?.toString() ?: ""
//            val expectedRepsText = set.reps?.toString() ?: ""
//
//            if (weightInput.text.toString() != expectedWeightText) {
//                weightInput.setText(expectedWeightText)
//                // Ripristina cursore se il campo aveva focus
//                if (weightCursorPos >= 0 && weightCursorPos <= expectedWeightText.length) {
//                    weightInput.setSelection(weightCursorPos)
//                }
//            }
//            if (repsInput.text.toString() != expectedRepsText) {
//                repsInput.setText(expectedRepsText)
//                // Ripristina cursore se il campo aveva focus
//                if (repsCursorPos >= 0 && repsCursorPos <= expectedRepsText.length) {
//                    repsInput.setSelection(repsCursorPos)
//                }
//            }            // Gestione checkbox completamento (solo in modalità allenamento)
//            if (isWorkoutMode) {
//                setCompletedCheckbox.visibility = View.VISIBLE
//                val isCompleted = sessionSetCompletionStatus[set.id] ?: false
//                setCompletedCheckbox.isChecked = isCompleted
//
//                // Colora la riga se completata
//                if (isCompleted) {
//                    setRowLayout.setBackgroundColor(
//                        itemView.context.getColor(R.color.primary)
//                    )
//                } else {
//                    setRowLayout.background = null
//                }
//
//                setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
//                    if (isChecked) {
//                        // Valida che peso e reps siano inseriti
//                        val weight = weightInput.text.toString().toDoubleOrNull()
//                        val reps = repsInput.text.toString().toIntOrNull()
//
//                        if (weight != null && reps != null) {
//                            val updatedSet = set.copy(
//                                weightKg = weight,
//                                reps = reps,
//                                updatedAt = System.currentTimeMillis()
//                            )
//                            onSetChanged(updatedSet)
//                            onSetCompleted(set.id)
//                        } else {
//                            setCompletedCheckbox.isChecked = false
//                            // Mostra messaggio di errore
//                            android.widget.Toast.makeText(
//                                itemView.context,
//                                "Inserisci peso e ripetizioni per completare la serie",
//                                android.widget.Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                    }
//                }
//            } else {
//                setCompletedCheckbox.visibility = View.GONE
//                setRowLayout.background = null
//            }
//
//            // Menu set (visibile solo in modalità edit)
//            setMenu.visibility = if (isWorkoutMode) View.GONE else View.VISIBLE
//            setMenu.setOnClickListener {
//                showSetMenu(set)
//            }
//
//            // Listener per cambiamenti input
//            setupInputListeners(set)
//        }
//
//        fun partialBind(set: ExerciseSet, payloads: List<Any>) {
//            for (p in payloads) {
//                when (p) {
//                    PAYLOAD_MODE_CHANGE -> {
//                        // Solo gestione visibilità checkbox/menu
//                        if (isWorkoutMode) {
//                            setCompletedCheckbox.visibility = View.VISIBLE
//                            val isCompleted = sessionSetCompletionStatus[set.id] ?: false
//                            setCompletedCheckbox.isChecked = isCompleted
//                        } else {
//                            setCompletedCheckbox.visibility = View.GONE
//                            setRowLayout.background = null
//                        }
//                        setMenu.visibility = if (isWorkoutMode) View.GONE else View.VISIBLE
//                    }
//                    PAYLOAD_COMPLETION_CHANGE -> {
//                        val isCompleted = sessionSetCompletionStatus[set.id] ?: false
//                        if (isWorkoutMode) {
//                            setCompletedCheckbox.isChecked = isCompleted
//                            if (isCompleted) {
//                                setRowLayout.setBackgroundColor(itemView.context.getColor(R.color.primary))
//                            } else {
//                                setRowLayout.background = null
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        private fun setupInputListeners(set: ExerciseSet) {
//            // Salva solo quando l'utente finisce di editare (perde il focus)
//            weightInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
//                if (!hasFocus) {
//                    val newWeight = weightInput.text.toString().toDoubleOrNull()
//                    if (newWeight != set.weightKg) {
//                        onSetChanged(set.copy(
//                            weightKg = newWeight,
//                            updatedAt = System.currentTimeMillis()
//                        ))
//                    }
//                }
//            }
//
//            repsInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
//                if (!hasFocus) {
//                    val newReps = repsInput.text.toString().toIntOrNull()
//                    if (newReps != set.reps) {
//                        onSetChanged(set.copy(
//                            reps = newReps,
//                            updatedAt = System.currentTimeMillis()
//                        ))
//                    }
//                }
//            }
//        }
//
//        private fun showSetMenu(set: ExerciseSet) {
//            val context = itemView.context
//            val options = arrayOf(
//                "Duplica serie",
//                "Inserisci serie sopra",
//                "Inserisci serie sotto",
//                "Elimina serie"
//            )
//
//            android.app.AlertDialog.Builder(context)
//                .setTitle("Serie ${set.setNumber}")
//                .setItems(options) { _, which ->
//                    when (which) {
//                        0 -> duplicateSet(set)
//                        1 -> insertSetAbove(set)
//                        2 -> insertSetBelow(set)
//                        3 -> onDeleteSet(set)
//                    }
//                }
//                .show()
//        }
//
//        private fun duplicateSet(set: ExerciseSet) {
//            // TODO: Implementare callback per aggiungere set duplicato
//            // val newSet = set.copy(
//            //     id = java.util.UUID.randomUUID().toString(),
//            //     setNumber = set.setNumber + 1,
//            //     createdAt = System.currentTimeMillis(),
//            //     updatedAt = System.currentTimeMillis()
//            // )
//        }
//
//        private fun insertSetAbove(set: ExerciseSet) {
//            // TODO: Implementare callback per inserire set sopra
//            // val newSet = ExerciseSet(...)
//        }
//
//        private fun insertSetBelow(set: ExerciseSet) {
//            // TODO: Implementare callback per inserire set sotto
//             val newSet = ExerciseSet(...)
//        }
//    }
//
//    object SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
//        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
//            return oldItem.id == newItem.id
//        }
//
//        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
//            return oldItem == newItem
//        }
//    }
//}

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
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(DiffCallback) {

    init {
        // Abilitiamo gli stable IDs per evitare il ricrearsi inutile delle view e perdita del focus
        setHasStableIds(true)
    }

    private var isEditMode = false
    private var isWorkoutActive = false
    val exerciseAdapters = mutableMapOf<String, ExerciseSetsAdapter>()

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    fun setWorkoutActive(workoutActive: Boolean) {
        isWorkoutActive = workoutActive
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        Collections.swap(currentList, fromPosition, toPosition)
        submitList(currentList)
    }

    fun cleanup() {
        exerciseAdapters.clear()
    }

    fun updateSessionSetCompletion(exerciseId: String, setId: String, isCompleted: Boolean) {
        exerciseAdapters[exerciseId]?.updateSetCompletionStatus(setId, isCompleted)
    }

    fun flushAllPendingUpdates() {
        // SEMPLIFICATO: Non ci sono più pending updates da gestire
        // Gli aggiornamenti vengono salvati immediatamente su focus loss
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        // Usa l'id dell'esercizio come stable id
        return getItem(position).exercise.id.hashCode().toLong()
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

            // Setup base info
            exerciseName.text = exercise.name
            exerciseInfo.text = "${sets.size} serie"

            // Gestione visibilità elementi UI
            setupUIForMode(exercise, sets)

            // Setup RecyclerView per i set
            setupSetsRecyclerView(exercise.id, sets)

            // Click listeners
            setupClickListeners(exercise)
        }

        private fun setupUIForMode(@Suppress("UNUSED_PARAMETER") exercise: WorkoutExercise, sets: List<ExerciseSet>) {
            // Drag handle visibile solo in modalità edit
            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE

            // Status icon visibile solo durante allenamento
            if (isWorkoutActive) {
                exerciseStatusIcon.visibility = View.VISIBLE
                // During workout, we need to check completion status from the session data
                // This will be updated via updateSetCompletionStatus method
                val allSetsCompleted = false // Will be managed by the parent component
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

            // Header e contenuto set
            if (sets.isEmpty()) {
                setsHeaderLayout.visibility = View.GONE
                setsRecyclerView.visibility = View.GONE
            } else {
                setsHeaderLayout.visibility = View.VISIBLE
                setsRecyclerView.visibility = View.VISIBLE
            }

            // Pulsante aggiungi serie sempre visibile
            addSetButton.visibility = View.VISIBLE

            // Rest timer visibile solo durante allenamento e se necessario
            restTimerCard.visibility = View.GONE // Sarà gestito dal ViewModel
        }

        private fun setupSetsRecyclerView(exerciseId: String, sets: List<ExerciseSet>) {
            val adapter = exerciseAdapters.getOrPut(exerciseId) {
                ExerciseSetsAdapter(
                    isWorkoutMode = isWorkoutActive,
                    onSetChanged = onSetChanged,
                    onSetCompleted = onSetCompleted,
                    onDeleteSet = onDeleteSet
                )
            }

            // MIGLIORAMENTO: Aggiorna la modalità solo se necessario
            adapter.updateWorkoutModeIfChanged(isWorkoutActive)
            
            // MIGLIORAMENTO: Usa submitList solo se la lista è effettivamente cambiata
            if (!sets.areListsEqual(adapter.currentList)) {
                adapter.submitList(sets.toList())
            }

            if (setsRecyclerView.adapter !== adapter) {
                setsRecyclerView.adapter = adapter
                if (setsRecyclerView.layoutManager == null) {
                    setsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
                }
                setsRecyclerView.isNestedScrollingEnabled = false
                setsRecyclerView.setHasFixedSize(true)
            }
        }

        // Helper per confrontare liste
        private fun List<ExerciseSet>.areListsEqual(other: List<ExerciseSet>): Boolean {
            if (this.size != other.size) return false
            return this.zip(other).all { (a, b) -> 
                a.id == b.id && a.setNumber == b.setNumber && 
                a.weightKg == b.weightKg && a.reps == b.reps 
            }
        }

        private fun setupClickListeners(exercise: WorkoutExercise) {
            // Click sul nome dell'esercizio
            exerciseName.setOnClickListener {
                onExerciseClick(exercise)
            }

            // Menu esercizio
            exerciseMenu.setOnClickListener {
                showExerciseMenu(exercise)
            }

            // Aggiungi serie
            addSetButton.setOnClickListener {
                onAddSetClick(exercise.id)
            }

            // Skip rest timer
            skipRestButton.setOnClickListener {
                restTimerCard.visibility = View.GONE
                // TODO: Notificare il ViewModel per fermare il timer
            }
        }

        private fun showExerciseMenu(exercise: WorkoutExercise) {
            val context = itemView.context
            val options = arrayOf(
                "Vedi dettagli esercizio",
                "Modifica nome",
                "Duplica esercizio",
                "Elimina esercizio"
            )

            android.app.AlertDialog.Builder(context)
                .setTitle(exercise.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onExerciseClick(exercise) // Vedi dettagli
                        1 -> showEditNameDialog(exercise)
                        2 -> duplicateExercise(exercise)
                        3 -> onDeleteExercise(exercise)
                    }
                }
                .show()
        }

        private fun showEditNameDialog(exercise: WorkoutExercise) {
            val context = itemView.context
            val editText = android.widget.EditText(context).apply {
                setText(exercise.name)
                selectAll()
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Modifica nome esercizio")
                .setView(editText)
                .setPositiveButton("Salva") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty() && newName != exercise.name) {
                        // TODO: Implementare callback per aggiornare nome
                        // onExerciseNameChanged(exercise.copy(name = newName))
                    }
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        @Suppress("UNUSED_PARAMETER")
        private fun duplicateExercise(exercise: WorkoutExercise) {
            // TODO: Implementare callback per duplicare esercizio
            // onDuplicateExercise(exercise)
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
    }
}

// Adapter separato per i set di ogni esercizio - VERSIONE SEMPLIFICATA
class ExerciseSetsAdapter(
    private var isWorkoutMode: Boolean,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onSetCompleted: (String) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<ExerciseSet, ExerciseSetsAdapter.SetViewHolder>(SetDiffCallback) {

    private val sessionSetCompletionStatus = mutableMapOf<String, Boolean>()

    init { setHasStableIds(true) }

    fun updateWorkoutModeIfChanged(workoutMode: Boolean) {
        if (isWorkoutMode != workoutMode) {
            isWorkoutMode = workoutMode
            // MIGLIORAMENTO: Usa partial update invece di notifyDataSetChanged completo
            notifyItemRangeChanged(0, itemCount, "MODE_CHANGE")
        }
    }

    fun updateSetCompletionStatus(setId: String, isCompleted: Boolean) {
        sessionSetCompletionStatus[setId] = isCompleted
        val index = currentList.indexOfFirst { it.id == setId }
        if (index != -1) {
            // Solo update del background, non degli input
            notifyItemChanged(index, "COMPLETION_ONLY")
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
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // MIGLIORAMENTO: Gestisci anche MODE_CHANGE payload
            for (payload in payloads) {
                when (payload) {
                    "COMPLETION_ONLY" -> {
                        holder.updateCompletionOnly(getItem(position))
                    }
                    "MODE_CHANGE" -> {
                        holder.updateModeOnly(getItem(position))
                    }
                }
            }
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val setRowLayout: View = itemView.findViewById(R.id.setRowLayout)
        private val setNumber: TextView = itemView.findViewById(R.id.setNumber)
        private val weightInput: com.google.android.material.textfield.TextInputEditText =
            itemView.findViewById(R.id.weightInput)
        private val repsInput: com.google.android.material.textfield.TextInputEditText =
            itemView.findViewById(R.id.repsInput)
        private val setCompletedCheckbox: com.google.android.material.checkbox.MaterialCheckBox =
            itemView.findViewById(R.id.setCompletedCheckbox)
        private val setMenu: ImageView = itemView.findViewById(R.id.setMenu)

        private var currentSet: ExerciseSet? = null
        private var isBindingInProgress = false
        private var isUserEditing = false // NUOVO: flag per tracking when user is actively editing
        private var textWatchersSetup = false // NUOVO: flag per evitare setup multipli

        fun bind(set: ExerciseSet) {
            isBindingInProgress = true
            currentSet = set

            setNumber.text = set.setNumber.toString()

            // PROTEZIONE ANTI-REBIND: Non aggiornare gli input se l'utente sta digitando
            if (!isUserEditing) {
                // Salva la posizione del cursore prima di aggiornare i valori
                val weightHasFocus = weightInput.hasFocus()
                val repsHasFocus = repsInput.hasFocus()
                val weightCursorPos = if (weightHasFocus) weightInput.selectionStart else -1
                val repsCursorPos = if (repsHasFocus) repsInput.selectionStart else -1

                // MIGLIORAMENTO: Imposta i valori SOLO se diversi da quello attuale
                // E ripristina la posizione del cursore se il campo aveva focus
                val expectedWeightText = set.weightKg?.toString() ?: ""
                val expectedRepsText = set.reps?.toString() ?: ""
                
                if (weightInput.text.toString() != expectedWeightText) {
                    weightInput.setText(expectedWeightText)
                    // Ripristina il cursore solo se il campo aveva focus e la posizione è valida
                    if (weightHasFocus && weightCursorPos >= 0 && weightCursorPos <= expectedWeightText.length) {
                        weightInput.post {
                            try {
                                weightInput.setSelection(weightCursorPos)
                            } catch (e: Exception) {
                                // Ignora errori di selezione
                            }
                        }
                    }
                }
                
                if (repsInput.text.toString() != expectedRepsText) {
                    repsInput.setText(expectedRepsText)
                    // Ripristina il cursore solo se il campo aveva focus e la posizione è valida
                    if (repsHasFocus && repsCursorPos >= 0 && repsCursorPos <= expectedRepsText.length) {
                        repsInput.post {
                            try {
                                repsInput.setSelection(repsCursorPos)
                            } catch (e: Exception) {
                                // Ignora errori di selezione
                            }
                        }
                    }
                }
            }

            updateCompletionStatus(set)
            setupModeVisibility(set)
            setupInputListenersSimplified(set)

            isBindingInProgress = false
        }

        fun updateCompletionOnly(set: ExerciseSet) {
            // Update solo del completion status senza toccare gli input
            updateCompletionStatus(set)
        }

        fun updateModeOnly(set: ExerciseSet) {
            // Update solo della modalità (visibilità checkbox/menu) senza toccare gli input
            setupModeVisibility(set)
        }

        private fun updateCompletionStatus(set: ExerciseSet) {
            if (isWorkoutMode) {
                setCompletedCheckbox.visibility = View.VISIBLE
                val isCompleted = sessionSetCompletionStatus[set.id] ?: false
                setCompletedCheckbox.isChecked = isCompleted

                // Colora la riga se completata
                if (isCompleted) {
                    setRowLayout.setBackgroundColor(
                        itemView.context.getColor(R.color.primary)
                    )
                } else {
                    setRowLayout.background = null
                }
            } else {
                setCompletedCheckbox.visibility = View.GONE
                setRowLayout.background = null
            }
        }

        private fun setupModeVisibility(set: ExerciseSet) {
            // Menu set (visibile solo in modalità edit)
            setMenu.visibility = if (isWorkoutMode) View.GONE else View.VISIBLE
            setMenu.setOnClickListener {
                showSetMenu(set)
            }

            // Checkbox listener
            setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !isBindingInProgress) {
                    // Valida che peso e reps siano inseriti
                    val weight = weightInput.text.toString().toDoubleOrNull()
                    val reps = repsInput.text.toString().toIntOrNull()

                    if (weight != null && reps != null) {
                        val updatedSet = set.copy(
                            weightKg = weight,
                            reps = reps,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSetChanged(updatedSet)
                        onSetCompleted(set.id)
                    } else {
                        setCompletedCheckbox.isChecked = false
                        android.widget.Toast.makeText(
                            itemView.context,
                            "Inserisci peso e ripetizioni per completare la serie",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun setupInputListenersSimplified(set: ExerciseSet) {
            // MIGLIORAMENTO: Setup dei listener solo una volta per ViewHolder
            if (!textWatchersSetup) {
                setupTextWatchers()
                textWatchersSetup = true
            }

            // Focus listeners possono essere aggiornati ad ogni bind perché cambiano il comportamento
            weightInput.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    isUserEditing = true
                } else {
                    isUserEditing = false
                    if (!isBindingInProgress && view.isAttachedToWindow) {
                        saveWeightValue(set)
                    }
                }
            }

            repsInput.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    isUserEditing = true
                } else {
                    isUserEditing = false
                    if (!isBindingInProgress && view.isAttachedToWindow) {
                        saveRepsValue(set)
                    }
                }
            }

            // Editor action listeners
            weightInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    saveWeightValue(set)
                    isUserEditing = false
                    false
                } else false
            }

            repsInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    saveRepsValue(set)
                    isUserEditing = false
                    false
                } else false
            }
        }

        private fun setupTextWatchers() {
            // TextWatcher per weightInput
            weightInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (weightInput.hasFocus()) {
                        isUserEditing = true
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            // TextWatcher per repsInput
            repsInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (repsInput.hasFocus()) {
                        isUserEditing = true
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        private fun saveWeightValue(set: ExerciseSet) {
            val newWeight = weightInput.text.toString().toDoubleOrNull()
            if (newWeight != set.weightKg) {
                val updatedSet = set.copy(
                    weightKg = newWeight,
                    updatedAt = System.currentTimeMillis()
                )
                onSetChanged(updatedSet)
            }
        }

        private fun saveRepsValue(set: ExerciseSet) {
            val newReps = repsInput.text.toString().toIntOrNull()
            if (newReps != set.reps) {
                val updatedSet = set.copy(
                    reps = newReps,
                    updatedAt = System.currentTimeMillis()
                )
                onSetChanged(updatedSet)
            }
        }

        private fun showSetMenu(set: ExerciseSet) {
            val context = itemView.context
            val options = arrayOf(
                "Duplica serie",
                "Inserisci serie sopra",
                "Inserisci serie sotto",
                "Elimina serie"
            )

            android.app.AlertDialog.Builder(context)
                .setTitle("Serie ${set.setNumber}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> duplicateSet(set)
                        1 -> insertSetAbove(set)
                        2 -> insertSetBelow(set)
                        3 -> onDeleteSet(set)
                    }
                }
                .show()
        }

        @Suppress("UNUSED_PARAMETER")
        private fun duplicateSet(set: ExerciseSet) {
            // TODO: Implementare callback per aggiungere set duplicato
        }

        @Suppress("UNUSED_PARAMETER")
        private fun insertSetAbove(set: ExerciseSet) {
            // TODO: Implementare callback per inserire set sopra
        }

        @Suppress("UNUSED_PARAMETER")
        private fun insertSetBelow(set: ExerciseSet) {
            // TODO: Implementare callback per inserire set sotto
        }
    }

    // DiffCallback MIGLIORATO
    object SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            // Confronta solo i campi che influenzano l'UI
            return oldItem.setNumber == newItem.setNumber &&
                    oldItem.weightKg == newItem.weightKg &&
                    oldItem.reps == newItem.reps
        }

        override fun getChangePayload(oldItem: ExerciseSet, newItem: ExerciseSet): Any? {
            // Se solo i dati di completamento sono cambiati, usa payload speciale
            if (oldItem.setNumber == newItem.setNumber &&
                oldItem.weightKg == newItem.weightKg &&
                oldItem.reps == newItem.reps) {
                return "COMPLETION_ONLY"
            }
            return null
        }
    }
}