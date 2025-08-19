package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.entities.WorkoutCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections


class WorkoutCardsAdapter(
    private val workoutDao: WorkoutDao,
    private val onCardClick: (WorkoutCard) -> Unit,
    private val onMenuClick: (WorkoutCard) -> Unit
) : ListAdapter<WorkoutCard, WorkoutCardsAdapter.ViewHolder>(DiffCallback) {

    private val items = mutableListOf<WorkoutCard>()

    override fun submitList(list: List<WorkoutCard>?) {
        super.submitList(list?.toList())
        items.clear()
        list?.let { items.addAll(it) }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(items, fromPosition, toPosition)
//        notifyItemMoved(fromPosition, toPosition)
    }

    fun getItems(): List<WorkoutCard> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_card, parent, false)
        return ViewHolder(view, workoutDao,onCardClick, onMenuClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val workoutDao: WorkoutDao,
        private val onCardClick: (WorkoutCard) -> Unit,
        private val onMenuClick: (WorkoutCard) -> Unit,
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

            CoroutineScope(Dispatchers.IO).launch {
                val count = workoutDao.getExerciseCountForCard(workoutCard.id)
                // Torna al thread principale per aggiornare l'UI
                withContext(Dispatchers.Main) {
                    exerciseCount.text = count.toString() + " exercises"
                }
            }

            // Formatta la data dell'ultimo aggiornamento
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val updateDate = Date(workoutCard.updatedAt)
            lastWorkout.text = "Created: ${dateFormat.format(updateDate)}"

            // Click listeners
            itemView.setOnClickListener { onCardClick(workoutCard) }
            workoutCardMenu.setOnClickListener { onMenuClick(workoutCard) }
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