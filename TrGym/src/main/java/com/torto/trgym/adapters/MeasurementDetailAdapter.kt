package com.torto.trgym.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
import com.torto.trgym.database.entities.BodyMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementDetailAdapter(
    private val onEditClick: (BodyMeasurement) -> Unit,
    private val onDeleteClick: (BodyMeasurement) -> Unit
) : RecyclerView.Adapter<MeasurementDetailAdapter.ViewHolder>() {

    private var measurements = listOf<BodyMeasurement>()

    fun submitList(newMeasurements: List<BodyMeasurement>) {
        measurements = newMeasurements
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(measurements[position])
    }

    override fun getItemCount() = measurements.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val valueTextView = itemView.findViewById<TextView>(R.id.textValue)
        private val dateTextView = itemView.findViewById<TextView>(R.id.textDate)
        private val notesTextView = itemView.findViewById<TextView>(R.id.textNotes)
        private val editButton = itemView.findViewById<ImageView>(R.id.btnEdit)
        private val deleteButton = itemView.findViewById<ImageView>(R.id.btnDelete)

        fun bind(measurement: BodyMeasurement) {
            valueTextView.text = "${measurement.value} ${measurement.unit}"

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateTextView.text = dateFormatter.format(Date(measurement.measurementDate))

            if (measurement.notes.isNullOrBlank()) {
                notesTextView.visibility = View.GONE
            } else {
                notesTextView.visibility = View.VISIBLE
                notesTextView.text = measurement.notes
            }

            editButton.setOnClickListener {
                onEditClick(measurement)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(measurement)
            }
        }
    }
}