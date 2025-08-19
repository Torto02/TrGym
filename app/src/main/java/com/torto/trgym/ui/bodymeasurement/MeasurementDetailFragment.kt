package com.torto.trgym.ui.bodymeasurement

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.database.entities.BodyMeasurement
import com.torto.trgym.database.dto.MeasurementStats
import com.torto.trgym.viewmodel.measurement.BodyMeasurementViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MeasurementDetailFragment : Fragment() {

    private val viewModel: BodyMeasurementViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MeasurementDetailAdapter
    private lateinit var categoryNameTextView: MaterialButton
    private lateinit var statsCard: MaterialCardView
    private lateinit var emptyView: LinearLayout
    private lateinit var fab: FloatingActionButton

    // Stats views
    private lateinit var latestValueTextView: TextView
    private lateinit var minValueTextView: TextView
    private lateinit var maxValueTextView: TextView
    private lateinit var avgValueTextView: TextView
    private lateinit var countTextView: TextView

    private var currentCategoryName: String? = null
    private var currentUnit: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_measurement_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Prima inizializziamo le views
        setupViews(view)
        setupRecyclerView()
        setupClickListeners()

        // Poi leggiamo gli argomenti passati dalla navigazione
        val categoryName = arguments?.getString("categoryName")
        val categoryUnit = arguments?.getString("categoryUnit") ?: ""

        if (categoryName != null) {
            currentCategoryName = categoryName
            currentUnit = categoryUnit
            categoryNameTextView.text = categoryName

            // Imposta la categoria selezionata nel ViewModel
            viewModel.selectMeasurementCategory(categoryName)

            // Carica i dati per questa categoria
            loadMeasurementsForCategory(categoryName)
            loadStatsForCategory(categoryName)


        } else {
            Log.e("MeasurementDetailFragment", "No category name provided in arguments")
            findNavController().navigateUp()
            return
        }
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewMeasurements)
        categoryNameTextView = view.findViewById(R.id.textCategoryName)
        statsCard = view.findViewById(R.id.cardStats)
        emptyView = view.findViewById(R.id.emptyView)
        fab = view.findViewById(R.id.fabAddMeasurement)

        // Stats views
        latestValueTextView = view.findViewById(R.id.textLatestValue)
        minValueTextView = view.findViewById(R.id.textMinValue)
        maxValueTextView = view.findViewById(R.id.textMaxValue)
        avgValueTextView = view.findViewById(R.id.textAvgValue)
        countTextView = view.findViewById(R.id.textCount)
    }

    private fun setupRecyclerView() {

        adapter = MeasurementDetailAdapter(
            onEditClick = { measurement ->
                showEditMeasurementDialog(measurement)
            },
            onDeleteClick = { measurement ->
                showDeleteConfirmationDialog(measurement)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            currentCategoryName?.let { categoryName ->
                showAddMeasurementDialog(categoryName, currentUnit)
            }
        }

        // Back button
        view?.findViewById<ImageView>(R.id.btnBack)?.setOnClickListener {
            val action = MeasurementDetailFragmentDirections.actionMeasurementDetailFragmentToBodyMeasurementsFragment()
            findNavController().navigate(action)
        }




        // Delete category button
        view?.findViewById<ImageView>(R.id.btnDeleteCategory)?.setOnClickListener {
            currentCategoryName?.let { categoryName ->
                showDeleteCategoryDialog(categoryName)
            }
        }
    }

    private fun loadMeasurementsForCategory(categoryName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMeasurementsForCategory(categoryName).collect { measurements ->
                if (measurements.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    statsCard.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    statsCard.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    adapter.submitList(measurements)

                    // Aggiorna l'unità dalla prima misurazione
                    currentUnit = measurements.firstOrNull()?.unit ?: ""
                }
            }
        }
    }

    private fun loadStatsForCategory(categoryName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = viewModel.getMeasurementStats(categoryName)
            stats?.let { updateStatsCard(it) }
        }
    }

    private fun updateStatsCard(stats: MeasurementStats) {
        latestValueTextView.text = "${stats.latestValue} ${stats.latestUnit}"
        minValueTextView.text = "${stats.minValue} ${stats.latestUnit}"
        maxValueTextView.text = "${stats.maxValue} ${stats.latestUnit}"
        avgValueTextView.text = "${String.format("%.1f", stats.averageValue)} ${stats.latestUnit}"
        countTextView.text = "${stats.count} misurazioni"
    }

    private fun showAddMeasurementDialog(categoryName: String, unit: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_measurement, null)

        val valueEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextValue)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextNotes)
        val unitTextView = dialogView.findViewById<TextView>(R.id.textViewUnit)

        unitTextView.text = unit

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Aggiungi Misurazione")
            .setMessage("Aggiungi un nuovo valore per: $categoryName")
            .setView(dialogView)
            .setPositiveButton("Aggiungi") { _, _ ->
                val valueText = valueEditText.text.toString().trim()
                val notes = notesEditText.text.toString().trim()

                if (valueText.isNotEmpty()) {
                    try {
                        val value = valueText.toDouble()
                        viewModel.addNewMeasurement(categoryName, value, unit, notes.takeIf { it.isNotEmpty() })
                        Toast.makeText(requireContext(), "Misurazione aggiunta", Toast.LENGTH_SHORT).show()
                        // Ricarica le statistiche
                        loadStatsForCategory(categoryName)
                    } catch (e: NumberFormatException) {
                        Toast.makeText(requireContext(), "Valore non valido", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Inserisci un valore", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showEditMeasurementDialog(measurement: BodyMeasurement) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_measurement, null)

        val valueEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextValue)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextNotes)
        val unitTextView = dialogView.findViewById<TextView>(R.id.textViewUnit)

        // Pre-popola i campi
        valueEditText.setText(measurement.value.toString())
        notesEditText.setText(measurement.notes ?: "")
        unitTextView.text = measurement.unit

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Modifica Misurazione")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val valueText = valueEditText.text.toString().trim()
                val notes = notesEditText.text.toString().trim()

                if (valueText.isNotEmpty()) {
                    try {
                        val value = valueText.toDouble()
                        viewModel.updateMeasurement(measurement, value, notes.takeIf { it.isNotEmpty() })
                        Toast.makeText(requireContext(), "Misurazione aggiornata", Toast.LENGTH_SHORT).show()
                        // Ricarica le statistiche
                        currentCategoryName?.let { loadStatsForCategory(it) }
                    } catch (e: NumberFormatException) {
                        Toast.makeText(requireContext(), "Valore non valido", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Inserisci un valore", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(measurement: BodyMeasurement) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Elimina Misurazione")
            .setMessage("Sei sicuro di voler eliminare questa misurazione?")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteMeasurement(measurement)
                Toast.makeText(requireContext(), "Misurazione eliminata", Toast.LENGTH_SHORT).show()
                // Ricarica le statistiche
                currentCategoryName?.let { loadStatsForCategory(it) }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteCategoryDialog(categoryName: String) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Elimina Categoria")
            .setMessage("Sei sicuro di voler eliminare tutte le misurazioni per '$categoryName'? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina Tutto") { _, _ ->
                viewModel.deleteMeasurementCategory(categoryName)
                Toast.makeText(requireContext(), "Categoria eliminata", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}

// ==========================================
// ADAPTER
// ==========================================

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