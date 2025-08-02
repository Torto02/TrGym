
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.dto.WorkoutExerciseDetail

class WorkoutExercisesAdapter(
    private val onAddSetClick: (String) -> Unit,
    private val onSetChanged: (ExerciseSet) -> Unit,
    private val onDeleteExercise: (WorkoutExercise) -> Unit,
    private val onDeleteSet: (ExerciseSet) -> Unit
) : ListAdapter<WorkoutExerciseDetail, WorkoutExercisesAdapter.ViewHolder>(DiffCallback) {

    // Mappa per mantenere lo stato di espansione degli esercizi
    private val expandedStates = mutableMapOf<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_exercise, parent, false)
        return ViewHolder(view, onAddSetClick, onSetChanged, onDeleteExercise, onDeleteSet, expandedStates)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Metodo per aggiornare solo un elemento specifico
    fun updateExerciseAtPosition(position: Int, exerciseDetail: WorkoutExerciseDetail) {
        val currentList = currentList.toMutableList()
        if (position < currentList.size) {
            currentList[position] = exerciseDetail
            submitList(currentList)
        }
    }

    class ViewHolder(
        itemView: View,
        private val onAddSetClick: (String) -> Unit,
        private val onSetChanged: (ExerciseSet) -> Unit,
        private val onDeleteExercise: (WorkoutExercise) -> Unit,
        private val onDeleteSet: (ExerciseSet) -> Unit,
        private val expandedStates: MutableMap<String, Boolean>
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
                // Prevent click propagation by consuming the event
                view.parent.requestDisallowInterceptTouchEvent(true)
                toggleExpansion()
            }

            // Blocca gli eventi touch sui container per impedire che si propaghino all'itemView
            setsContainer.isClickable = true
            setsContainer.isFocusable = true
            setsContainer.setOnClickListener {
                // Nessuna azione, solo bloccare la propagazione
            }

            // Blocca gli eventi touch sul container dinamico
            dynamicSetsContainer.isClickable = true
            dynamicSetsContainer.isFocusable = true
            dynamicSetsContainer.setOnClickListener {
                // Nessuna azione, solo bloccare la propagazione
            }
        }

        fun bind(exerciseDetail: WorkoutExerciseDetail) {
            currentExerciseDetail = exerciseDetail

            exerciseName.text = exerciseDetail.exercise.name

            // Impedisce la propagazione del click sul nome dell'esercizio
            exerciseName.setOnClickListener { view ->
                // Prevent click propagation
                view.parent.requestDisallowInterceptTouchEvent(true)
                toggleExpansion()
            }

            // Setup del pulsante aggiungi set
            addSetBtn.setOnClickListener { view -> 
                // Impedisce la propagazione del click all'itemView
                view.parent.requestDisallowInterceptTouchEvent(true)
                // Interrompe eventuali animazioni pendenti
                view.clearAnimation()
                
                // Prima di aggiungere una nuova serie, forza il salvataggio di tutti i set esistenti
                // per assicurarsi che i dati appena inseriti non vengano persi
                forceSaveCurrentInputs()
                
                // Chiamata del callback
                onAddSetClick(exerciseDetail.exercise.id)
            }

            // Long click per eliminare esercizio
            itemView.setOnLongClickListener {
                onDeleteExercise(exerciseDetail.exercise)
                true
            }

            // Ripristina lo stato di espansione
            val isExpanded = expandedStates[exerciseDetail.exercise.id] ?: false
            updateExpansionState(isExpanded)

            // Popola i set
            populateSets(exerciseDetail.sets)
        }

        private fun toggleExpansion() {
            val exerciseId = currentExerciseDetail?.exercise?.id ?: return
            val currentState = expandedStates[exerciseId] ?: false
            val newState = !currentState
            
            expandedStates[exerciseId] = newState
            updateExpansionState(newState)
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

        /**
         * Forza il salvataggio di tutti i valori correnti negli EditText
         * per assicurarsi che non vengano persi quando si aggiunge una nuova serie
         */
        private fun forceSaveCurrentInputs() {
            // Salva i valori correnti prima di qualsiasi aggiornamento della lista
            currentExerciseDetail?.sets?.forEach { set ->
                val setView = setViews[set.id] ?: return@forEach
                
                val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
                val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)
                
                // Ottiene i valori correnti
                val weightText = weightEditText.text?.toString()
                val repsText = repsEditText.text?.toString()
                
                // Converte e salva solo se c'è un cambiamento effettivo
                val weightKg = weightText?.toDoubleOrNull()
                val reps = repsText?.toIntOrNull()
                
                // Aggiorna il set solo se i valori sono diversi da quelli nel set
                if (weightKg != set.weightKg || reps != set.reps) {
                    val updatedSet = set.copy(weightKg = weightKg, reps = reps)
                    onSetChanged(updatedSet)
                }
            }
        }

        private fun populateSets(sets: List<ExerciseSet>) {
            // Salva i valori correnti degli EditText prima di ricreare le viste
            val currentValues = mutableMapOf<String, Pair<String?, String?>>()
            
            // Salva i valori correnti prima di rimuovere le viste
            setViews.forEach { (setId, view) ->
                val weightEditText = view.findViewById<TextInputEditText>(R.id.weightEditText)
                val repsEditText = view.findViewById<TextInputEditText>(R.id.repsEditText)
                
                // Salva il testo attuale
                val weightText = weightEditText.text?.toString()
                val repsText = repsEditText.text?.toString()
                
                currentValues[setId] = Pair(weightText, repsText)
            }
            
            // Rimuove tutte le viste esistenti
            dynamicSetsContainer.removeAllViews()
            setViews.clear()

            // Ricrea le viste per ogni set
            sets.forEach { set ->
                val setView = createSetView(set)
                
                // Ripristina i valori salvati, se esistenti
                if (currentValues.containsKey(set.id)) {
                    val (weightText, repsText) = currentValues[set.id]!!
                    val weightEditText = setView.findViewById<TextInputEditText>(R.id.weightEditText)
                    val repsEditText = setView.findViewById<TextInputEditText>(R.id.repsEditText)
                    
                    // Ripristina i valori solo se sono stati modificati dall'utente
                    if (!weightText.isNullOrEmpty() && weightText != "null") {
                        weightEditText.setText(weightText)
                    }
                    if (!repsText.isNullOrEmpty() && repsText != "null") {
                        repsEditText.setText(repsText)
                    }
                }
                
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

            setNumber.text = set.setNumber.toString()
            weightEditText.setText(set.weightKg?.toString() ?: "")
            repsEditText.setText(set.reps?.toString() ?: "")

            // Impedisce la propagazione del click sui campi di input
            weightEditText.isClickable = true
            weightEditText.isFocusable = true
            weightEditText.setOnClickListener { view ->
                // Prevent click propagation
                view.parent.requestDisallowInterceptTouchEvent(true)
            }
            
            repsEditText.isClickable = true
            repsEditText.isFocusable = true
            repsEditText.setOnClickListener { view ->
                // Prevent click propagation
                view.parent.requestDisallowInterceptTouchEvent(true)
            }

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

            // Long click per eliminare con feedback visivo
            setView.setOnLongClickListener {
                deleteSetIcon.visibility = View.VISIBLE
                setView.alpha = 0.7f
                
                // Nascondi l'icona dopo 2 secondi se non viene premuta
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
                       oldSet.reps == newSet.reps
                   }
        }
    }
}