//package com.torto.trgym.adapters
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.torto.trgym.R
//import com.torto.trgym.database.entities.WorkoutCard
//import java.text.SimpleDateFormat
//import java.util.*
//
//class WorkoutCardsAdapter(
//    private val onCardClick: (WorkoutCard) -> Unit,
//    private val onMenuClick: (WorkoutCard) -> Unit
//) : ListAdapter<WorkoutCard, WorkoutCardsAdapter.ViewHolder>(DiffCallback) {
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_workout_card, parent, false)
//        return ViewHolder(view, onCardClick, onMenuClick)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        holder.bind(getItem(position))
//    }
//
//    class ViewHolder(
//        itemView: View,
//        private val onCardClick: (WorkoutCard) -> Unit,
//        private val onMenuClick: (WorkoutCard) -> Unit
//    ) : RecyclerView.ViewHolder(itemView) {
//
//        private val workoutCardName: TextView = itemView.findViewById(R.id.workoutCardName)
//        private val workoutCardDescription: TextView = itemView.findViewById(R.id.workoutCardDescription)
//        private val exerciseCount: TextView = itemView.findViewById(R.id.exerciseCount)
//        private val lastWorkout: TextView = itemView.findViewById(R.id.lastWorkout)
//        private val workoutCardMenu: ImageView = itemView.findViewById(R.id.workoutCardMenu)
//
//        fun bind(workoutCard: WorkoutCard) {
//            workoutCardName.text = workoutCard.name
//
//            // Mostra la descrizione solo se presente
//            if (workoutCard.description.isNullOrEmpty()) {
//                workoutCardDescription.visibility = View.GONE
//            } else {
//                workoutCardDescription.visibility = View.VISIBLE
//                workoutCardDescription.text = workoutCard.description
//            }
//
//            // TODO: Implementare il conteggio degli esercizi tramite repository
//            exerciseCount.text = "0 esercizi" // Placeholder
//
//            // Formatta la data dell'ultimo aggiornamento
//            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
//            val updateDate = Date(workoutCard.updatedAt)
//            lastWorkout.text = "Creata: ${dateFormat.format(updateDate)}"
//
//            // Click listeners
//            itemView.setOnClickListener { onCardClick(workoutCard) }
//            workoutCardMenu.setOnClickListener { onMenuClick(workoutCard) }
//        }
//    }
//
//    object DiffCallback : DiffUtil.ItemCallback<WorkoutCard>() {
//        override fun areItemsTheSame(oldItem: WorkoutCard, newItem: WorkoutCard): Boolean {
//            return oldItem.id == newItem.id
//        }
//
//        override fun areContentsTheSame(oldItem: WorkoutCard, newItem: WorkoutCard): Boolean {
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.data.repository.WorkoutSessionRepository
import com.torto.trgym.database.entities.WorkoutCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WorkoutCardsAdapter(
    private val onCardClick: (WorkoutCard) -> Unit,
    private val onMenuClick: (WorkoutCard) -> Unit,
    private val workoutRepository: WorkoutRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<WorkoutCard, WorkoutCardsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_card, parent, false)
        return ViewHolder(view, onCardClick, onMenuClick, workoutRepository, workoutSessionRepository, lifecycleOwner)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onCardClick: (WorkoutCard) -> Unit,
        private val onMenuClick: (WorkoutCard) -> Unit,
        private val workoutRepository: WorkoutRepository,
        private val workoutSessionRepository: WorkoutSessionRepository,
        private val lifecycleOwner: LifecycleOwner
    ) : RecyclerView.ViewHolder(itemView) {

        private val workoutCardName: TextView = itemView.findViewById(R.id.workoutCardName)
        private val workoutCardDescription: TextView = itemView.findViewById(R.id.workoutCardDescription)
        private val exerciseCount: TextView = itemView.findViewById(R.id.exerciseCount)
        private val lastWorkout: TextView = itemView.findViewById(R.id.lastWorkout)
        private val workoutCardMenu: ImageView = itemView.findViewById(R.id.workoutCardMenu)

        fun bind(workoutCard: WorkoutCard) {
            workoutCardName.text = workoutCard.name

            // Mostra la descrizione solo se presente
            if (workoutCard.description.isNullOrEmpty()) {
                workoutCardDescription.visibility = View.GONE
            } else {
                workoutCardDescription.visibility = View.VISIBLE
                workoutCardDescription.text = workoutCard.description
            }

            // Carica il conteggio degli esercizi
            loadExerciseCount(workoutCard.id)

            // Carica informazioni sull'ultimo allenamento
            loadLastWorkoutInfo(workoutCard.id)

            // Click listeners
            itemView.setOnClickListener { onCardClick(workoutCard) }
            workoutCardMenu.setOnClickListener { onMenuClick(workoutCard) }
        }

        private fun loadExerciseCount(cardId: String) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val exercises = workoutRepository.getExercisesForCard(cardId).first()
                    val count = exercises.size
                    exerciseCount.text = if (count == 1) {
                        "1 esercizio"
                    } else {
                        "$count esercizi"
                    }
                } catch (e: Exception) {
                    exerciseCount.text = "0 esercizi"
                }
            }
        }

        private fun loadLastWorkoutInfo(cardId: String) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val sessions = workoutSessionRepository.getSessionsForCard(cardId).first()
                    val lastSession = sessions.firstOrNull { it.isCompleted }

                    if (lastSession != null) {
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        val sessionDate = Date(lastSession.startTime)
                        lastWorkout.text = "Ultimo: ${dateFormat.format(sessionDate)}"
                    } else {
                        lastWorkout.text = "Mai eseguita"
                    }
                } catch (e: Exception) {
                    lastWorkout.text = "Info non disponibile"
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WorkoutCard>() {
        override fun areItemsTheSame(oldItem: WorkoutCard, newItem: WorkoutCard): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WorkoutCard, newItem: WorkoutCard): Boolean {
            return oldItem == newItem
        }
    }
}