package com.torto.trgym.ui.bodymeasurement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.viewmodel.bodymeasurement.BodyMeasurementViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class BodyMeasurementsFragment : Fragment() {

    private val viewModel: BodyMeasurementViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MeasurementCategoryAdapter
    private lateinit var emptyView: LinearLayout
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_body_measurements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewMeasurements)
        emptyView = view.findViewById(R.id.emptyView)
        fab = view.findViewById(R.id.fabAddMeasurement)
    }

    private fun setupRecyclerView() {
        adapter = MeasurementCategoryAdapter(
            onCategoryClick = { category ->
                // Navigate to detail view for this category passando il nome come argomento
                val bundle = Bundle().apply {
                    putString("categoryName", category.name)
                    putString("categoryUnit", category.unit)
                }
                findNavController().navigate(
                    R.id.action_bodyMeasurementsFragment_to_measurementDetailFragment,
                    bundle
                )
            },
            onAddMeasurementClick = { category ->
                showAddMeasurementDialog(category.name, category.unit)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showCreateCategoryDialog()
        }

        // Back button
        view?.findViewById<MaterialButton>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is BodyMeasurementViewModel.BodyMeasurementUiState.Loading -> {
                            // Show loading
                        }
                        is BodyMeasurementViewModel.BodyMeasurementUiState.Success -> {
                            if (state.categories.isEmpty()) {
                                recyclerView.visibility = View.GONE
                                emptyView.visibility = View.VISIBLE
                            } else {
                                recyclerView.visibility = View.VISIBLE
                                emptyView.visibility = View.GONE
                                adapter.submitList(state.categories)
                            }
                        }
                        is BodyMeasurementViewModel.BodyMeasurementUiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showCreateCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_measurement_category, null)

        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextCategoryName)
        val unitEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextUnit)
        val valueEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextValue)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextNotes)

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Nuova Categoria")
            .setMessage("Crea una nuova categoria di misurazione e aggiungi il primo valore")
            .setView(dialogView)
            .setPositiveButton("Crea") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val unit = unitEditText.text.toString().trim()
                val valueText = valueEditText.text.toString().trim()
                val notes = notesEditText.text.toString().trim()

                if (name.isNotEmpty() && unit.isNotEmpty() && valueText.isNotEmpty()) {
                    try {
                        val value = valueText.toDouble()
                        viewModel.addNewMeasurement(name, value, unit, notes.takeIf { it.isNotEmpty() })
                    } catch (e: NumberFormatException) {
                        Toast.makeText(requireContext(), "Valore non valido", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Compila tutti i campi obbligatori", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showAddMeasurementDialog(categoryName: String, unit: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_measurement, null)

        val valueEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextValue)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextNotes)
        val unitTextView = dialogView.findViewById<TextView>(R.id.textViewUnit)

        unitTextView.text = unit

        MaterialAlertDialogBuilder(requireContext())
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
}

// ==========================================
// ADAPTER
// ==========================================

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
            countTextView.text = "${category.totalCount} misurazioni"

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