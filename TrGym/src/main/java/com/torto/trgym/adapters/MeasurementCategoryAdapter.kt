package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.viewmodel.measurement.BodyMeasurementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MeasurementCategoryAdapter(
    private val onCategoryClick: (BodyMeasurementViewModel.MeasurementCategory) -> Unit,
    private val onAddMeasurementClick: (BodyMeasurementViewModel.MeasurementCategory) -> Unit
) : RecyclerView.Adapter<MeasurementCategoryAdapter.ViewHolder>() {

    private var categories = listOf<BodyMeasurementViewModel.MeasurementCategory>()

    fun submitList(newCategories: List<BodyMeasurementViewModel.MeasurementCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardCategory)
        private val nameTextView = itemView.findViewById<TextView>(R.id.textCategoryName)
        private val latestValueTextView = itemView.findViewById<TextView>(R.id.textLatestValue)
        private val countTextView = itemView.findViewById<TextView>(R.id.textMeasurementCount)
        private val dateTextView = itemView.findViewById<TextView>(R.id.textLatestDate)
        private val addButton = itemView.findViewById<ImageView>(R.id.btnAddMeasurement)

        fun bind(category: BodyMeasurementViewModel.MeasurementCategory) {
            nameTextView.text = category.name
            countTextView.text = "${category.totalCount} measurements"

            if (category.latestValue != null) {
                latestValueTextView.text = "${category.latestValue} ${category.unit}"
                latestValueTextView.visibility = View.VISIBLE

                category.latestDate?.let { date ->
                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    dateTextView.text = formatter.format(Date(date))
                    dateTextView.visibility = View.VISIBLE
                }
            } else {
                latestValueTextView.visibility = View.GONE
                dateTextView.visibility = View.GONE
            }

            cardView.setOnClickListener {
                onCategoryClick(category)
            }

            addButton.setOnClickListener {
                onAddMeasurementClick(category)
            }
        }
    }
}