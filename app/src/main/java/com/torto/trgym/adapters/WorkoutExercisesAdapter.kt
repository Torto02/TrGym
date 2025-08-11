//package com.torto.trgym.adapters
//
//import android.text.Editable
//import android.text.TextWatcher
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.google.android.material.button.MaterialButton
//import com.google.android.material.textfield.TextInputEditText
//import com.torto.trgym.R
//import com.torto.trgym.database.entities.ExerciseSet
//import com.torto.trgym.database.entities.WorkoutExercise
//import com.torto.trgym.database.dto.WorkoutExerciseDetail
//import kotlinx.coroutines.*
//
//class WorkoutExercisesAdapter(
//    private val onAddSetClick: (String) -> Unit,
//    private val onSetChanged: (ExerciseSet) -> Unit,
//    private val onDeleteExercise: (WorkoutExercise) -> Unit,
//    private val onDeleteSet: (ExerciseSet) -> Unit
//) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ViewHolder>(DiffCallback) {
//
//    // Mappa per mantenere lo stato di espansione degli esercizi
//    private val expandedStates = mutableMapOf<String, Boolean>()
//
//    // Scope per gestire il debouncing
//    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_workout_exercise, parent, false)
//        return ViewHolder(view, onAddSetClick, onSetChanged, onDeleteExercise, onDeleteSet, expandedStates, adapterScope)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        holder.bind(getItem(position))
//    }
//
//    // Metodo per aggiornare solo un elemento specifico
//    fun updateExerciseAtPosition(position: Int, exerciseDetail: WorkoutExerciseDetail) {
//        val currentList = currentList.toMutableList()
//        if (position < currentList.size) {
//            currentList[position] = exerciseDetail
//            submitList(currentList)
//        }
//    }
//
//    // Cleanup method to cancel any pending coroutines
//    fun cleanup() {
//        adapterScope.cancel()
//    }
//
//    class ViewHolder(
//        itemView: View,
//        private val onAddSetClick: (String) -> Unit,
//        private val onSetChanged: (ExerciseSet) -> Unit,
//        private val onDeleteExercise: (WorkoutExercise) -> Unit,
//        private val onDeleteSet: (ExerciseSet) -> Unit,
//        private val expandedStates: MutableMap<String, Boolean>,
//        private val adapterScope: CoroutineScope
//    ) : RecyclerView.ViewHolder(itemView) {
//
//        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
//        private val expandCollapseIcon: ImageView = itemView.findViewById(R.id.expandCollapseIcon)
//        private val setsContainer: LinearLayout = itemView.findViewById(R.id.setsContainer)
//        private val dynamicSetsContainer: LinearLayout = itemView.findViewById(R.id.dynamicSetsContainer)
//        private val addSetBtn: MaterialButton = itemView.findViewById(R.id.addSetBtn)
//
//        private var currentExerciseDetail: WorkoutExerciseDetail? = null
//        private val setViews = mutableMapOf<String, View>()
//
//        init {
//            // Click listener per espandere/comprimere
//            itemView.setOnClickListener {
//                toggleExpansion()
//            }
//
//            expandCollapseIcon.setOnClickListener { view ->
//                // Prevent click propagation by consuming the event
//                view.parent.requestDisallowInterceptTouchEvent(true)
//                toggleExpansion()
//            }
//
//            // Setup del pulsante aggiungi set
//            addSetBtn.setOnClickListener { view ->
//                // Impedisce la propagazione del click all'itemView
//                view.parent.requestDisallowInterceptTouchEvent(true)
//                // Interrompe eventuali animazioni pendenti
//                view.clearAnimation()
//
//                // Prima di aggiungere una nuova serie, forza il salvataggio di tutti i set esistenti
//                forceSaveCurrentInputs()
//
//                // Chiamata del callback con l'ID dell'esercizio corrente
//                currentExerciseDetail?.exercise?.id?.let { exerciseId ->
//                    onAddSetClick(exerciseId)
//                }
//            }
//
//            // Blocca gli eventi touch sui container per impedire che si propaghino all'itemView
//            setsContainer.isClickable = true
//            setsContainer.isFocusable = true
//            setsContainer.setOnClickListener {
//                // Nessuna azione, solo bloccare la propagazione
//            }
//
//            // Blocca gli eventi touch sul container dinamico
//            dynamicSetsContainer.isClickable = true
//            dynamicSetsContainer.isFocusable = true
//            dynamicSetsContainer.setOnClickListener {
//                // Nessuna azione, solo bloccare la propagazione
//            }
//        }
//
//        fun bind(exerciseDetail: WorkoutExerciseDetail) {
//            // Store current exercise detail for later use
//            currentExerciseDetail = exerciseDetail
//
//            // Set text only if it has changed to avoid unnecessary UI updates
//            if (exerciseName.text.toString() != exerciseDetail.exercise.name) {
//                exerciseName.text = exerciseDetail.exercise.name
//            }
//
//            // Long click for deleting exercise - set once per bind to avoid memory leaks
//            itemView.setOnLongClickListener {
//                onDeleteExercise(exerciseDetail.exercise)
//                true
//            }
//
//            // Restore expansion state with minimal UI updates
//            val isExpanded = expandedStates[exerciseDetail.exercise.id] ?: false
//            if (setsContainer.visibility == View.VISIBLE != isExpanded) {
//                updateExpansionState(isExpanded)
//            }
//
//            // Only repopulate sets if necessary
//            if (isExpanded || setViews.isEmpty()) {
//                populateSets(exerciseDetail.sets)
//            }
//        }
//
//        private fun toggleExpansion() {
//            val exerciseId = currentExerciseDetail?.exercise?.id ?: return
//            val currentState = expandedStates[exerciseId] ?: false
//            val newState = !currentState
//
//            expandedStates[exerciseId] = newState
//            updateExpansionState(newState)
//        }
//
//        private fun updateExpansionState(isExpanded: Boolean) {
//            if (isExpanded) {
//                setsContainer.visibility = View.VISIBLE
//                expandCollapseIcon.setImageResource(R.drawable.ic_ios_back)
//                expandCollapseIcon.rotation = 90f
//            } else {
//                setsContainer.visibility = View.GONE
//                expandCollapseIcon.setImageResource(R.drawable.ic_ios_back)
//                expandCollapseIcon.rotation = 270f
//            }
//        }
//
//        /**
//         * Forza il salvataggio di tutti i valori correnti negli EditText
//         * per assicurarsi che non vengano persi quando si aggiunge una nuova serie
//         */
//        private fun forceSaveCurrentInputs() {
//            // Salva i valori correnti prima di qualsiasi aggiornamento della lista
//            currentExerciseDetail?.sets?.forEach { set ->
//                val setView = setViews[set.id] ?: return@forEach
//
//                val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
//                val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)
//
//                // Ottiene i valori correnti
//                val weightText = weightEditText.text?.toString()
//                val repsText = repsEditText.text?.toString()
//
//                // Converte e salva solo se c'è un cambiamento effettivo
//                val weightKg = weightText?.toDoubleOrNull()
//                val reps = repsText?.toIntOrNull()
//
//                // Aggiorna il set solo se i valori sono diversi da quelli nel set
//                if (weightKg != set.weightKg || reps != set.reps) {
//                    val updatedSet = set.copy(weightKg = weightKg, reps = reps)
//                    onSetChanged(updatedSet)
//                }
//            }
//        }
//
//        private fun populateSets(sets: List<ExerciseSet>) {
//            // Map to track existing views by set ID
//            val existingSets = mutableMapOf<String, View>()
//            val setsToAdd = mutableListOf<ExerciseSet>()
//            val setIdsToKeep = sets.map { it.id }.toSet()
//
//            // First, identify views that can be reused and those to be removed
//            setViews.entries.toList().forEach { (setId, view) ->
//                if (setId in setIdsToKeep) {
//                    // This view can be reused
//                    existingSets[setId] = view
//                } else {
//                    // This set no longer exists, remove its view
//                    dynamicSetsContainer.removeView(view)
//                    setViews.remove(setId)
//                }
//            }
//
//            // Identify sets that need new views
//            sets.forEach { set ->
//                if (!existingSets.containsKey(set.id)) {
//                    setsToAdd.add(set)
//                }
//            }
//
//            // Clear all views if order has changed, to ensure correct ordering
//            if (dynamicSetsContainer.childCount != sets.size ||
//                !sets.mapIndexed { index, set ->
//                    index < dynamicSetsContainer.childCount &&
//                            setViews[set.id] == dynamicSetsContainer.getChildAt(index)
//                }.all { it }) {
//
//                // Order changed, remove all and rebuild
//                dynamicSetsContainer.removeAllViews()
//
//                // Add all sets in the correct order
//                sets.forEach { set ->
//                    val view = existingSets[set.id] ?: createSetView(set)
//                    updateSetView(view, set)
//                    dynamicSetsContainer.addView(view)
//                    setViews[set.id] = view
//                }
//            } else {
//                // Order is maintained, just update existing views and add new ones
//                sets.forEach { set ->
//                    if (set.id in existingSets.keys) {
//                        // Update existing view
//                        updateSetView(existingSets[set.id]!!, set)
//                    } else {
//                        // Create and add new view
//                        val newView = createSetView(set)
//                        dynamicSetsContainer.addView(newView)
//                        setViews[set.id] = newView
//                    }
//                }
//            }
//        }
//
//        private fun updateSetView(view: View, set: ExerciseSet) {
//            // Update the view with current set data
//            val setNumber = view.findViewById<TextView>(R.id.setNumber)
//            val weightEditText = view.findViewById<TextInputEditText>(R.id.weightEditText)
//            val repsEditText = view.findViewById<TextInputEditText>(R.id.repsEditText)
//
//            // Only update EditText values if they are not focused and only when absolutely necessary
//            if (!weightEditText.hasFocus()) {
//                val currentText = weightEditText.text?.toString()?.trim() ?: ""
//                val newValue = set.weightKg?.toString() ?: ""
//
//                // Only update if the current field is truly empty and we have a database value to show
//                // OR if this is a completely new view (currentText is empty and no user input has occurred)
//                if (currentText.isEmpty() && newValue.isNotEmpty()) {
//                    weightEditText.setText(newValue)
//                }
//                // Do NOT update if user has entered something, even if it differs from database
//                // This preserves user input during navigation
//            }
//
//            if (!repsEditText.hasFocus()) {
//                val currentText = repsEditText.text?.toString()?.trim() ?: ""
//                val newValue = set.reps?.toString() ?: ""
//
//                // Only update if the current field is truly empty and we have a database value to show
//                // OR if this is a completely new view (currentText is empty and no user input has occurred)
//                if (currentText.isEmpty() && newValue.isNotEmpty()) {
//                    repsEditText.setText(newValue)
//                }
//                // Do NOT update if user has entered something, even if it differs from database
//                // This preserves user input during navigation
//            }
//
//            // Set number should always be updated
//            setNumber.text = set.setNumber.toString()
//        }
//
//        private fun createSetView(set: ExerciseSet): View {
//            val setView = LayoutInflater.from(itemView.context)
//                .inflate(R.layout.item_exercise_set, dynamicSetsContainer, false)
//
//            val setNumber = setView.findViewById<TextView>(R.id.setNumber)
//            val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
//            val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)
//            val deleteSetIcon = setView.findViewById<ImageView>(R.id.deleteSetIcon)
//
//            setNumber.text = set.setNumber.toString()
//            weightEditText.setText(set.weightKg?.toString() ?: "")
//            repsEditText.setText(set.reps?.toString() ?: "")
//
//            // Store set ID as tag for reference
//            weightEditText.tag = set.id
//            repsEditText.tag = set.id
//
//            // Impedisce la propagazione del click sui campi di input
//            weightEditText.isClickable = true
//            weightEditText.isFocusable = true
//            weightEditText.setOnClickListener { view ->
//                // Prevent click propagation
//                view.parent.requestDisallowInterceptTouchEvent(true)
//            }
//
//            repsEditText.isClickable = true
//            repsEditText.isFocusable = true
//            repsEditText.setOnClickListener { view ->
//                // Prevent click propagation
//                view.parent.requestDisallowInterceptTouchEvent(true)
//            }
//
//            // Variables to store debounce jobs
//            var weightDebounceJob: Job? = null
//            var repsDebounceJob: Job? = null
//
//            // TextWatcher per il peso con debouncing
//            weightEditText.addTextChangedListener(object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//                override fun afterTextChanged(s: Editable?) {
//                    // Cancel previous job
//                    weightDebounceJob?.cancel()
//
//                    // Start new debounced job
//                    weightDebounceJob = adapterScope.launch {
//                        delay(500) // Wait 500ms before saving
//                        val weight = s.toString().toDoubleOrNull()
//                        val updatedSet = set.copy(weightKg = weight)
//                        onSetChanged(updatedSet)
//                    }
//                }
//            })
//
//            // TextWatcher per le ripetizioni con debouncing
//            repsEditText.addTextChangedListener(object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//                override fun afterTextChanged(s: Editable?) {
//                    // Cancel previous job
//                    repsDebounceJob?.cancel()
//
//                    // Start new debounced job
//                    repsDebounceJob = adapterScope.launch {
//                        delay(500) // Wait 500ms before saving
//                        val reps = s.toString().toIntOrNull()
//                        val updatedSet = set.copy(reps = reps)
//                        onSetChanged(updatedSet)
//                    }
//                }
//            })
//
//            // Long click per eliminare con feedback visivo
//            setView.setOnLongClickListener {
//                deleteSetIcon.visibility = View.VISIBLE
//                setView.alpha = 0.7f
//
//                // Nascondi l'icona dopo 2 secondi se non viene premuta
//                setView.postDelayed({
//                    deleteSetIcon.visibility = View.GONE
//                    setView.alpha = 1.0f
//                }, 2000)
//
//                onDeleteSet(set)
//                true
//            }
//
//            // Click sull'icona di eliminazione
//            deleteSetIcon.setOnClickListener { view ->
//                view.parent.requestDisallowInterceptTouchEvent(true)
//                onDeleteSet(set)
//            }
//
//            return setView
//        }
//    }
//
//    object DiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
//        override fun areItemsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
//            return oldItem.exercise.id == newItem.exercise.id
//        }
//
//        override fun areContentsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
//            return oldItem.exercise == newItem.exercise &&
//                    oldItem.sets.size == newItem.sets.size &&
//                    oldItem.sets.zip(newItem.sets).all { (oldSet, newSet) ->
//                        oldSet.id == newSet.id &&
//                                oldSet.weightKg == newSet.weightKg &&
//                                oldSet.reps == newSet.reps
//                    }
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
import kotlinx.coroutines.*

class WorkoutExercisesAdapter(
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ViewHolder>(DiffCallback) {

    // Mappa per mantenere lo stato di espansione degli esercizi
    private val expandedStates = mutableMapOf<String, Boolean>()

    // Scope per gestire il debouncing
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ViewHolder(view, onAddSetClick, onSetChanged, onDeleteExercise, onDeleteSet, expandedStates, adapterScope)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Cleanup method to cancel any pending coroutines
    fun cleanup() {
        adapterScope.cancel()
    }

    class ViewHolder(
        itemView: View,
        private val onAddSetClick: (String) -> Unit,
        private val onSetChanged: (ExerciseSet) -> Unit,
        private val onDeleteExercise: (WorkoutExercise) -> Unit,
        private val onDeleteSet: (ExerciseSet) -> Unit,
        private val expandedStates: MutableMap<String, Boolean>,
        private val adapterScope: CoroutineScope
    ) : RecyclerView.ViewHolder(itemView) {

        private val exerciseName: TextView = itemView.findViewById(R.id.exerciseName)
        private val expandCollapseIcon: ImageView = itemView.findViewById(R.id.expandCollapseIcon)
        private val setsContainer: LinearLayout = itemView.findViewById(R.id.setsContainer)
        private val dynamicSetsContainer: LinearLayout = itemView.findViewById(R.id.dynamicSetsContainer)
        private val addSetBtn: MaterialButton = itemView.findViewById(R.id.addSetBtn)

        private var currentExerciseDetail: WorkoutExerciseDetail? = null
        private val setViews = mutableMapOf<String, View>()

        init {
            // Click listener per espandere/comprimere
            itemView.setOnClickListener {
                toggleExpansion()
            }

            expandCollapseIcon.setOnClickListener { view ->
                view.parent.requestDisallowInterceptTouchEvent(true)
                toggleExpansion()
            }

            // Setup del pulsante aggiungi set
            addSetBtn.setOnClickListener { view ->
                view.parent.requestDisallowInterceptTouchEvent(true)
                view.clearAnimation()

                currentExerciseDetail?.exercise?.id?.let { exerciseId ->
                    onAddSetClick(exerciseId)
                }
            }

            // Blocca gli eventi touch sui container
            setsContainer.isClickable = true
            setsContainer.isFocusable = true
            setsContainer.setOnClickListener { /* Blocca propagazione */ }

            dynamicSetsContainer.isClickable = true
            dynamicSetsContainer.isFocusable = true
            dynamicSetsContainer.setOnClickListener { /* Blocca propagazione */ }
        }

        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            currentExerciseDetail = exerciseDetail

            if (exerciseName.text.toString() != exerciseDetail.exercise.name) {
                exerciseName.text = exerciseDetail.exercise.name
            }

            itemView.setOnLongClickListener {
                onDeleteExercise(exerciseDetail.exercise)
                true
            }

            // Restore expansion state
            val isExpanded = expandedStates[exerciseDetail.exercise.id] ?: false
            if (setsContainer.visibility == View.VISIBLE != isExpanded) {
                updateExpansionState(isExpanded)
            }

            // Always populate sets when binding to ensure data consistency
            if (isExpanded) {
                populateSets(exerciseDetail.sets)
            }
        }

        private fun toggleExpansion() {
            val exerciseId = currentExerciseDetail?.exercise?.id ?: return
            val currentState = expandedStates[exerciseId] ?: false
            val newState = !currentState

            expandedStates[exerciseId] = newState
            updateExpansionState(newState)

            // Populate sets when expanding to ensure fresh data
            if (newState) {
                currentExerciseDetail?.let { populateSets(it.sets) }
            }
        }

        private fun updateExpansionState(isExpanded: Boolean) {
            if (isExpanded) {
                setsContainer.visibility = View.VISIBLE
                expandCollapseIcon.setImageResource(R.drawable.ic_ios_back)
                expandCollapseIcon.rotation = 90f
            } else {
                setsContainer.visibility = View.GONE
                expandCollapseIcon.setImageResource(R.drawable.ic_ios_back)
                expandCollapseIcon.rotation = 270f
            }
        }

        private fun populateSets(sets: List<ExerciseSet>) {
            // Rimuovi tutte le view esistenti e ricrea da zero per garantire consistenza
            dynamicSetsContainer.removeAllViews()
            setViews.clear()

            // Crea una nuova view per ogni set
            sets.forEach { set ->
                val setView = createSetView(set)
                dynamicSetsContainer.addView(setView)
                setViews[set.id] = setView
            }
        }

        private fun createSetView(set: ExerciseSet): View {
            val setView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.item_exercise_set, dynamicSetsContainer, false)

            val setNumber = setView.findViewById<TextView>(R.id.setNumber)
            val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
            val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)
            val deleteSetIcon = setView.findViewById<ImageView>(R.id.deleteSetIcon)

            // Imposta sempre i valori dal database quando crei la view
            setNumber.text = set.setNumber.toString()
            weightEditText.setText(set.weightKg?.toString() ?: "")
            repsEditText.setText(set.reps?.toString() ?: "")

            // Store set ID as tag for reference
            weightEditText.tag = set.id
            repsEditText.tag = set.id

            // Impedisce la propagazione del click sui campi di input
            weightEditText.isClickable = true
            weightEditText.isFocusable = true
            weightEditText.setOnClickListener { view ->
                view.parent.requestDisallowInterceptTouchEvent(true)
            }

            repsEditText.isClickable = true
            repsEditText.isFocusable = true
            repsEditText.setOnClickListener { view ->
                view.parent.requestDisallowInterceptTouchEvent(true)
            }

            // Variables to store debounce jobs
            var weightDebounceJob: Job? = null
            var repsDebounceJob: Job? = null

            // TextWatcher per il peso con debouncing
            weightEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Cancel previous job
                    weightDebounceJob?.cancel()

                    // Start new debounced job
                    weightDebounceJob = adapterScope.launch {
                        delay(300) // Ridotto a 300ms per feedback più veloce
                        val weight = s.toString().toDoubleOrNull()
                        val updatedSet = set.copy(weightKg = weight)
                        onSetChanged(updatedSet)
                    }
                }
            })

            // TextWatcher per le ripetizioni con debouncing
            repsEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Cancel previous job
                    repsDebounceJob?.cancel()

                    // Start new debounced job
                    repsDebounceJob = adapterScope.launch {
                        delay(300) // Ridotto a 300ms per feedback più veloce
                        val reps = s.toString().toIntOrNull()
                        val updatedSet = set.copy(reps = reps)
                        onSetChanged(updatedSet)
                    }
                }
            })

            // Long click per eliminare
            setView.setOnLongClickListener {
                deleteSetIcon.visibility = View.VISIBLE
                setView.alpha = 0.7f

                setView.postDelayed({
                    deleteSetIcon.visibility = View.GONE
                    setView.alpha = 1.0f
                }, 2000)

                onDeleteSet(set)
                true
            }

            // Click sull'icona di eliminazione
            deleteSetIcon.setOnClickListener { view ->
                view.parent.requestDisallowInterceptTouchEvent(true)
                onDeleteSet(set)
            }

            return setView
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WorkoutExerciseDetail>() {
        override fun areItemsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
            return oldItem.exercise.id == newItem.exercise.id
        }

        override fun areContentsTheSame(oldItem: WorkoutExerciseDetail, newItem: WorkoutExerciseDetail): Boolean {
            return oldItem.exercise == newItem.exercise &&
                    oldItem.sets.size == newItem.sets.size &&
                    oldItem.sets.zip(newItem.sets).all { (oldSet, newSet) ->
                        oldSet.id == newSet.id &&
                                oldSet.weightKg == newSet.weightKg &&
                                oldSet.reps == newSet.reps &&
                                oldSet.setNumber == newSet.setNumber
                    }
        }
    }
}