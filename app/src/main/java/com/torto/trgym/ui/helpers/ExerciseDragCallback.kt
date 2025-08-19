package com.torto.trgym.ui.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ExerciseDragCallback(
    private val onItemMoved: (fromPosition: Int, toPosition: Int) -> Unit
) : ItemTouchHelper.Callback() {

    private var isEnabled = false

    fun setDragEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (isEnabled) {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            makeMovementFlags(dragFlags, 0)
        } else {
            0
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
            onItemMoved(fromPosition, toPosition)
            return true
        }
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Non implementiamo lo swipe per eliminare in questo caso
    }

    override fun isLongPressDragEnabled(): Boolean {
        // Disabilitiamo il long press perché vogliamo usare il drag handle
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        // Disabilitiamo lo swipe
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Evidenzia l'elemento durante il drag
            viewHolder?.itemView?.alpha = 0.8f
            viewHolder?.itemView?.scaleX = 1.05f
            viewHolder?.itemView?.scaleY = 1.05f
            viewHolder?.itemView?.elevation = 8f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // Ripristina lo stato normale
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f
        viewHolder.itemView.elevation = 2f
    }
}