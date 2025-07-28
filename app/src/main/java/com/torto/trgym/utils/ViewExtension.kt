package com.torto.trgym.utils

import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import com.torto.trgym.R

/**
 * Mostra un popup riciclabile con una lista di stringhe, ancorato alla View chiamante.
 */
fun View.showListPopup(
    items: List<String>,
    width: Int = ListPopupWindow.WRAP_CONTENT,
    onItemClick: (position: Int) -> Unit
) {
    val popup = ListPopupWindow(context).apply {
        setAnchorView(this@showListPopup)
        setAdapter(ArrayAdapter(context, R.layout.item_popup_list, items))
        setWidth(width)
        isModal = true
        setOnItemClickListener { _, _, position, _ ->
            onItemClick(position)
            dismiss()
        }
    }
    popup.show()
}