//package com.torto.trgym.adapters
//
//import android.text.Editable
//import android.text.TextWatcher
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ItemTouchHelper
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
//    private var isEditMode = false
//    private var isWorkoutActive = false
//    private val exerciseAdapters = mutableMapOf<String, ExerciseSetsAdapter>()
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
//    // Aggiunta del metodo per payload binding
//    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int, payloads: MutableList<Any>) {
//        if (payloads.isEmpty()) {
//            super.onBindViewHolder(holder, position, payloads)
//        } else {
//            // Aggiornamento parziale - non fare rebind completo
//            holder.updatePartial(getItem(position))
//        }
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
//            setupClickListeners(exercise, sets)
//        }
//
//        // Nuovo metodo per aggiornamenti parziali
//        fun updatePartial(exerciseDetail: WorkoutExerciseDetail) {
//            val exercise = exerciseDetail.exercise
//            val sets = exerciseDetail.sets
//
//            // Aggiorna solo le informazioni necessarie senza toccare gli input
//            exerciseInfo.text = "${sets.size} serie"
//
//            // Aggiorna status icon se necessario
//            if (isWorkoutActive) {
//                val allSetsCompleted = sets.all { it.reps != null && it.weightKg != null }
//                if (allSetsCompleted && sets.isNotEmpty()) {
//                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
//                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.primary))
//                    exerciseCard.strokeColor = itemView.context.getColor(R.color.primary)
//                } else {
//                    exerciseStatusIcon.setImageResource(R.drawable.ic_email)
//                    exerciseStatusIcon.setColorFilter(itemView.context.getColor(R.color.ios_secondaryLabel))
//                    exerciseCard.strokeColor = itemView.context.getColor(R.color.lightGray)
//                }
//            }
//
//            // Aggiorna l'adapter dei set con payload per evitare rebind completo
//            exerciseAdapters[exercise.id]?.updateSetsPartial(sets)
//        }
//
//        private fun setupUIForMode(exercise: WorkoutExercise, sets: List<ExerciseSet>) {
//            // Drag handle visibile solo in modalità edit
//            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE
//
//            // Status icon visibile solo durante allenamento
//            if (isWorkoutActive) {
//                exerciseStatusIcon.visibility = View.VISIBLE
//                val allSetsCompleted = sets.all { it.reps != null && it.weightKg != null }
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
//            adapter.updateWorkoutMode(isWorkoutActive)
//            adapter.submitList(sets)
//
//            setsRecyclerView.apply {
//                if (this.adapter == null) {
//                    this.adapter = adapter
//                    layoutManager = LinearLayoutManager(context)
//                    isNestedScrollingEnabled = false
//                    setHasFixedSize(true)
//                }
//            }
//        }
//
//        private fun setupClickListeners(exercise: WorkoutExercise, sets: List<ExerciseSet>) {
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
//            // TODO: Implementare menu popup con opzioni:
//            // - Vedi dettagli esercizio
//            // - Modifica nome
//            // - Elimina esercizio
//            // - Sposta su/giù (se non in modalità drag)
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
//
//        // Aggiunta del payload per aggiornamenti parziali
//        override fun getChangePayload(
//            oldItem: WorkoutExerciseDetail,
//            newItem: WorkoutExerciseDetail
//        ): Any? {
//            return if (oldItem.exercise == newItem.exercise) {
//                "SETS_CHANGED" // Payload per indicare che sono cambiati solo i set
//            } else {
//                null
//            }
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
//    fun updateWorkoutMode(workoutMode: Boolean) {
//        isWorkoutMode = workoutMode
//        notifyDataSetChanged()
//    }
//
//    // Nuovo metodo per aggiornamenti parziali
//    fun updateSetsPartial(newSets: List<ExerciseSet>) {
//        submitList(newSets)
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
//    // Payload binding per evitare rebind completo
//    override fun onBindViewHolder(holder: SetViewHolder, position: Int, payloads: MutableList<Any>) {
//        if (payloads.isEmpty()) {
//            super.onBindViewHolder(holder, position, payloads)
//        } else {
//            holder.updatePartial(getItem(position))
//        }
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
//        private var currentSet: ExerciseSet? = null
//        private var isBinding = false
//
//        fun bind(set: ExerciseSet) {
//            isBinding = true
//            currentSet = set
//
//            setNumber.text = set.setNumber.toString()
//
//            // Imposta i valori degli input solo se sono diversi da quelli correnti
//            val currentWeight = weightInput.text.toString()
//            val newWeight = set.weightKg?.toString() ?: ""
//            if (currentWeight != newWeight) {
//                weightInput.setText(newWeight)
//            }
//
//            val currentReps = repsInput.text.toString()
//            val newReps = set.reps?.toString() ?: ""
//            if (currentReps != newReps) {
//                repsInput.setText(newReps)
//            }
//
//            updateUI(set)
//            setupInputListeners(set)
//
//            isBinding = false
//        }
//
//        fun updatePartial(set: ExerciseSet) {
//            currentSet = set
//            updateUI(set)
//        }
//
//        private fun updateUI(set: ExerciseSet) {
//            // Gestione checkbox completamento (solo in modalità allenamento)
//            if (isWorkoutMode) {
//                setCompletedCheckbox.visibility = View.VISIBLE
//                val isCompleted = set.weightKg != null && set.reps != null
//                setCompletedCheckbox.isChecked = isCompleted
//
//                // Colora la riga se completata
//                if (isCompleted) {
//                    setRowLayout.setBackgroundColor(
//                        itemView.context.getColor(R.color.gray)
//                    )
//                } else {
//                    setRowLayout.background = null
//                }
//
//                setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
//                    if (isChecked && !isBinding) {
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
//                            // TODO: Mostra messaggio di errore
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
//        }
//
//        private fun setupInputListeners(set: ExerciseSet) {
//            // Rimuovi listener esistenti per evitare conflitti
//            weightInput.clearFocus()
//            repsInput.clearFocus()
//
//            // Usa TextWatcher invece di OnFocusChangeListener per un controllo più preciso
//            weightInput.addTextChangedListener(object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//                override fun afterTextChanged(s: Editable?) {
//                    if (!isBinding && currentSet != null) {
//                        val weight = s.toString().toDoubleOrNull()
//                        if (weight != currentSet!!.weightKg) {
//                            val updatedSet = currentSet!!.copy(
//                                weightKg = weight,
//                                updatedAt = System.currentTimeMillis()
//                            )
//                            onSetChanged(updatedSet)
//                        }
//                    }
//                }
//            })
//
//            repsInput.addTextChangedListener(object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//                override fun afterTextChanged(s: Editable?) {
//                    if (!isBinding && currentSet != null) {
//                        val reps = s.toString().toIntOrNull()
//                        if (reps != currentSet!!.reps) {
//                            val updatedSet = currentSet!!.copy(
//                                reps = reps,
//                                updatedAt = System.currentTimeMillis()
//                            )
//                            onSetChanged(updatedSet)
//                        }
//                    }
//                }
//            })
//        }
//
//        private fun showSetMenu(set: ExerciseSet) {
//            // TODO: Implementare menu popup con opzioni:
//            // - Duplica serie
//            // - Elimina serie
//            // - Inserisci serie sopra/sotto
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
//
//        override fun getChangePayload(oldItem: ExerciseSet, newItem: ExerciseSet): Any? {
//            return if (oldItem.id == newItem.id &&
//                (oldItem.weightKg != newItem.weightKg || oldItem.reps != newItem.reps)) {
//                "VALUE_CHANGED"
//            } else {
//                null
//            }
//        }
//    }
//}
package com.torto.trgym.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

class WorkoutExercisesAdapter(
    private val onExerciseClick: (WorkoutExercise) -> Unit,
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onSetCompleted: (String) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit,
    private val onSaveChanges: () -> Unit // Nuovo callback per salvare quando si esce dalla modalità edit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ExerciseViewHolder>(DiffCallback) {

    private var isEditMode = false
    private var isWorkoutActive = false
    private val exerciseAdapters = mutableMapOf<String, ExerciseSetsAdapter>()
    private val pendingChanges = mutableMapOf<String, ExerciseSet>() // Memorizza le modifiche temporanee

    fun setEditMode(editMode: Boolean) {
        val wasInEditMode = isEditMode
        isEditMode = editMode

        // Se stiamo uscendo dalla modalità edit, salva tutte le modifiche
        if (wasInEditMode && !editMode) {
            saveAllPendingChanges()
        }

        // Aggiorna tutti gli adapter dei set
        exerciseAdapters.values.forEach { adapter ->
            adapter.setEditMode(editMode)
        }

        notifyDataSetChanged()
    }

    private fun saveAllPendingChanges() {
        pendingChanges.values.forEach { set ->
            onSetChanged(set)
        }
        pendingChanges.clear()
        onSaveChanges()
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
        exerciseAdapters.values.forEach { it.cleanup() }
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

            // Setup base info
            exerciseName.text = exercise.name
            exerciseInfo.text = "${sets.size} serie"

            // Gestione visibilità elementi UI
            setupUIForMode(exercise, sets)

            // Setup RecyclerView per i set
            setupSetsRecyclerView(exercise.id, sets)

            // Click listeners
            setupClickListeners(exercise, sets)
        }

        // Nuovo metodo per aggiornamenti parziali
        fun updatePartial(exerciseDetail: WorkoutExerciseDetail) {
            val exercise = exerciseDetail.exercise
            val sets = exerciseDetail.sets

            // Aggiorna solo le informazioni necessarie senza toccare gli input
            exerciseInfo.text = "${sets.size} serie"

            // Aggiorna status icon se necessario
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

            // Aggiorna l'adapter dei set con payload per evitare rebind completo
            exerciseAdapters[exercise.id]?.let { adapter ->
                adapter.setEditMode(isEditMode)
                adapter.updateSetsPartial(sets)
            }
        }

        private fun setupUIForMode(exercise: WorkoutExercise, sets: List<ExerciseSet>) {
            // Drag handle visibile solo in modalità edit
            dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE

            // Status icon visibile solo durante allenamento
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
                    isEditMode = isEditMode,
                    onSetChanged = { set ->
                        // Memorizza le modifiche temporaneamente invece di salvarle subito
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
            // TODO: Implementare menu popup con opzioni:
            // - Vedi dettagli esercizio
            // - Modifica nome
            // - Elimina esercizio
            // - Sposta su/giù (se non in modalità drag)
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

    // Nuovo metodo per aggiornamenti parziali
    fun updateSetsPartial(newSets: List<ExerciseSet>) {
        submitList(newSets)
    }

    fun cleanup() {
        // Cleanup di tutti i ViewHolder
        val recyclerView = getCurrentRecyclerView()
        recyclerView?.let { rv ->
            for (i in 0 until rv.childCount) {
                val viewHolder = rv.getChildViewHolder(rv.getChildAt(i)) as? SetViewHolder
                viewHolder?.cleanup()
            }
        }
    }

    private fun getCurrentRecyclerView(): RecyclerView? {
        // Questo è un hack per ottenere il RecyclerView corrente
        // In alternativa, potresti mantenere un riferimento al RecyclerView
        return null // Per ora lasciamo null, il cleanup principale avviene nel ViewHolder
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_set, parent, false)
        return SetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Payload binding per evitare rebind completo
    override fun onBindViewHolder(holder: SetViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.updatePartial(getItem(position))
        }
    }

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
        private var isBinding = false

        fun bind(set: ExerciseSet) {
            isBinding = true
            currentSet = set

            setNumber.text = set.setNumber.toString()

            // Imposta i valori degli input
            weightInput.setText(set.weightKg?.toString() ?: "")
            repsInput.setText(set.reps?.toString() ?: "")

            // Abilita/disabilita input basandosi sulla modalità
            val canEdit = isEditMode && !isWorkoutMode
            weightInput.isEnabled = canEdit
            repsInput.isEnabled = canEdit

            updateUI(set)

            if (canEdit) {
                setupInputListeners(set)
            }

            isBinding = false
        }

        fun updatePartial(set: ExerciseSet) {
            currentSet = set

            // Aggiorna solo se non siamo in modalità edit per evitare conflitti
            if (!isEditMode) {
                isBinding = true
                weightInput.setText(set.weightKg?.toString() ?: "")
                repsInput.setText(set.reps?.toString() ?: "")
                isBinding = false
            }

            updateUI(set)
        }

        private fun updateUI(set: ExerciseSet) {
            // Gestione checkbox completamento (solo in modalità allenamento)
            if (isWorkoutMode) {
                setCompletedCheckbox.visibility = View.VISIBLE
                val isCompleted = set.weightKg != null && set.reps != null
                setCompletedCheckbox.isChecked = isCompleted

                // Colora la riga se completata
                if (isCompleted) {
                    setRowLayout.setBackgroundColor(
                        itemView.context.getColor(R.color.gray)
                    )
                } else {
                    setRowLayout.background = null
                }

                setCompletedCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked && !isBinding) {
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
                            // TODO: Mostra messaggio di errore
                        }
                    }
                }
            } else {
                setCompletedCheckbox.visibility = View.GONE
                setRowLayout.background = null
            }

            // Menu set (visibile solo in modalità edit)
            setMenu.visibility = if (isEditMode && !isWorkoutMode) View.VISIBLE else View.GONE
            setMenu.setOnClickListener {
                showSetMenu(set)
            }

            // Stile input basato sulla modalità
            val canEdit = isEditMode && !isWorkoutMode
            val alpha = if (canEdit) 1.0f else 0.7f
            weightInput.alpha = alpha
            repsInput.alpha = alpha
        }

        private fun setupInputListeners(set: ExerciseSet) {
            // Listener semplici che aggiornano solo quando l'utente perde il focus
            weightInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isBinding && currentSet != null) {
                    val weight = weightInput.text.toString().toDoubleOrNull()
                    if (weight != currentSet!!.weightKg) {
                        val updatedSet = currentSet!!.copy(
                            weightKg = weight,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSetChanged(updatedSet)
                        currentSet = updatedSet
                    }
                }
            }

            repsInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isBinding && currentSet != null) {
                    val reps = repsInput.text.toString().toIntOrNull()
                    if (reps != currentSet!!.reps) {
                        val updatedSet = currentSet!!.copy(
                            reps = reps,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSetChanged(updatedSet)
                        currentSet = updatedSet
                    }
                }
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