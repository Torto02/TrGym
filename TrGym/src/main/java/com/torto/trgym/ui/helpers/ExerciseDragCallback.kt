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

        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                // Evidenzia l'elemento durante il drag
                viewHolder?.itemView?.let { view ->
                    view.alpha = 0.8f
                    view.scaleX = 1.02f
                    view.scaleY = 1.02f
                    view.elevation = 8f
                    // Aggiungi un leggero shadow per maggiore visibilità
                    view.translationZ = 4f
                }
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // Ripristina completamente lo stato normale
        viewHolder.itemView.let { view ->
            view.alpha = 1.0f
            view.scaleX = 1.0f
            view.scaleY = 1.0f
            view.elevation = 2f
            view.translationZ = 0f
            view.translationX = 0f
            view.translationY = 0f
        }

        // Forza l'invalidazione del ViewHolder per evitare artefatti visivi
        viewHolder.itemView.invalidate()

        // Piccolo delay per assicurarsi che le animazioni siano completate
        viewHolder.itemView.post {
            recyclerView.invalidateItemDecorations()
        }
    }

    override fun interpolateOutOfBoundsScroll(
        recyclerView: RecyclerView,
        viewSize: Int,
        viewSizeOutOfBounds: Int,
        totalSize: Int,
        msSinceStartScroll: Long
    ): Int {
        // Scroll più fluido durante il drag
        val direction = Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
        return (viewSize * 0.1f * direction).toInt()
    }
}