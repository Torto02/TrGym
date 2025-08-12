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

    // RIMOSSO: updateSessionSetCompletion per evitare rebinding
    // fun updateSessionSetCompletion(exerciseId: String, setId: String, isCompleted: Boolean) { ... }

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

            // RIMOSSO: Tutti i controlli di sincronizzazione che causano rebinding
            // adapter.updateWorkoutModeIfChanged(isWorkoutActive)
            // adapter.submitList(sets.toList())

            // Setup della RecyclerView solo se necessario
            if (setsRecyclerView.adapter !== adapter) {
                setsRecyclerView.adapter = adapter
                if (setsRecyclerView.layoutManager == null) {
                    setsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
                }
                setsRecyclerView.isNestedScrollingEnabled = false
                setsRecyclerView.setHasFixedSize(true)
                
                // Submit list solo alla prima configurazione
                adapter.submitList(sets.toList())
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
                "Elimina esercizio"
            )

            android.app.AlertDialog.Builder(context)
                .setTitle(exercise.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onExerciseClick(exercise) // Vedi dettagli
                        1  -> onDeleteExercise(exercise)
                    }
                }
                .show()
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

    // RIMOSSO: updateWorkoutModeIfChanged per evitare notifyDataSetChanged
    // fun updateWorkoutModeIfChanged(workoutMode: Boolean) { ... }

    // RIMOSSO: updateSetCompletionStatus per evitare notifyItemChanged che causa rebind
    // fun updateSetCompletionStatus(setId: String, isCompleted: Boolean) { ... }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_set, parent, false)
        return SetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // RIMOSSO: onBindViewHolder con payloads per evitare partial updates che causano rebind

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

            // ANTI-REBIND: Aggiorna i valori SOLO se il campo non ha focus (l'utente non sta digitando)
            if (!weightInput.hasFocus()) {
                val expectedWeightText = set.weightKg?.toString() ?: ""
                if (weightInput.text.toString() != expectedWeightText) {
                    weightInput.setText(expectedWeightText)
                }
            }
            
            if (!repsInput.hasFocus()) {
                val expectedRepsText = set.reps?.toString() ?: ""
                if (repsInput.text.toString() != expectedRepsText) {
                    repsInput.setText(expectedRepsText)
                }
            }

            updateCompletionStatus(set)
            setupModeVisibility(set)
            
            // Setup listener solo una volta
            if (!textWatchersSetup) {
                setupInputListenersSimplified(set)
                setupTextWatchers()
                textWatchersSetup = true
            }

            isBindingInProgress = false
        }

        // RIMOSSI: Metodi di update parziale per evitare rebinding
        // fun updateCompletionOnly(set: ExerciseSet) { ... }
        // fun updateModeOnly(set: ExerciseSet) { ... }

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
            // Pulsante elimina serie (visibile solo in modalità edit)
            setMenu.visibility = if (isWorkoutMode) View.GONE else View.VISIBLE
            setMenu.setOnClickListener {
                showDeleteSetDialog(set)
            }

            // Checkbox listener
            // Checkbox listener - UNICO punto di salvataggio
            setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !isBindingInProgress) {
                    // Valida che peso e reps siano inseriti
                    val weight = weightInput.text.toString().toDoubleOrNull()
                    val reps = repsInput.text.toString().toIntOrNull()

                    if (weight != null && reps != null) {
                        // UNICO salvataggio: solo quando il set viene completato
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

        @Suppress("UNUSED_PARAMETER")
        private fun setupInputListenersSimplified(set: ExerciseSet) {
            // RIMOSSO TUTTO: Nessun salvataggio automatico per evitare rebinding/refresh
            // L'utente dovrà salvare manualmente o il salvataggio avverrà solo alla chiusura
            
            // Solo gestione focus per il tracking, nessun salvataggio
            weightInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                isUserEditing = hasFocus
            }

            repsInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                isUserEditing = hasFocus
            }

            // NESSUN Editor Action Listener - nessun salvataggio automatico
            weightInput.setOnEditorActionListener(null)
            repsInput.setOnEditorActionListener(null)
        }

        private fun setupTextWatchers() {
            // SEMPLIFICATO: Solo tracking dell'editing, nessun salvataggio
            weightInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (weightInput.hasFocus()) {
                        isUserEditing = true
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

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

        // RIMOSSI: Metodi di salvataggio automatico per evitare rebinding
        // private fun saveWeightValue(set: ExerciseSet) { ... }
        // private fun saveRepsValue(set: ExerciseSet) { ... }

        private fun showDeleteSetDialog(set: ExerciseSet) {
            val context = itemView.context
            
            android.app.AlertDialog.Builder(context)
                .setTitle("Elimina serie")
                .setMessage("Sei sicuro di voler eliminare la serie ${set.setNumber}?")
                .setPositiveButton("Elimina") { _, _ ->
                    onDeleteSet(set)
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

    }

    // DiffCallback DISABILITATO per evitare rebinding sui cambiamenti di peso/reps
    object SetDiffCallback : DiffUtil.ItemCallback<ExerciseSet>() {
        override fun areItemsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExerciseSet, newItem: ExerciseSet): Boolean {
            // SEMPRE TRUE per evitare rebinding automatico sui cambiamenti di contenuto
            // Gli input mantengono i loro valori internamente senza rebind
            return true
        }

        override fun getChangePayload(oldItem: ExerciseSet, newItem: ExerciseSet): Any? {
            // Nessun payload per evitare partial update che causano rebind
            return null
        }
    }
}