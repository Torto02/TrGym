package com.torto.trgym.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutCardsAdapter
import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.entities.WorkoutSession
import com.torto.trgym.viewmodel.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var workoutCardsAdapter: WorkoutCardsAdapter
    private lateinit var activeWorkoutCard: MaterialCardView

    @Inject
    lateinit var workoutDao: WorkoutDao  // Aggiungo l'iniezione di WorkoutDao

    override fun onResume() {
        super.onResume()
        viewModel.refreshHome()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView(view)
        observeViewModel()
        checkForActiveWorkout()
    }

    private fun setupViews(view: View) {
        val addWorkoutCardBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.addWorkoutCardBtn)
        activeWorkoutCard = view.findViewById(R.id.activeWorkoutCard)


        addWorkoutCardBtn.setOnClickListener {
            showCreateWorkoutCardDialog()
        }
        
        activeWorkoutCard.setOnClickListener {
            navigateToActiveWorkout()
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.workoutCardsRecyclerView)
        workoutCardsAdapter = WorkoutCardsAdapter(
            workoutDao = workoutDao,  // Passo il workoutDao all'adapter
            onCardClick = { workoutCard ->
                val action = HomeFragmentDirections.actionHomeToWorkoutDetail(workoutCard.id)
                findNavController().navigate(action)
            },
            onMenuClick = { workoutCard ->
                showWorkoutCardMenu(workoutCard)
            }

        )

        recyclerView.apply {
            adapter = workoutCardsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                workoutCardsAdapter.onItemMove(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) = Unit

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                // qui il drag è finito, salviamo l'ordine definitivo
                val reordered = workoutCardsAdapter.getItems()
                    .mapIndexed { idx, card ->
                        card.copy(orderIndex = idx, updatedAt = System.currentTimeMillis())
                    }
                viewModel.reorderWorkoutCards(reordered)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.workoutCards.collect { workoutCards ->
                    workoutCardsAdapter.submitList(workoutCards)
                    updateEmptyState(workoutCards.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeViewModel.UiState.Loading -> {
                            // Mostra loading se necessario
                        }
                        is HomeViewModel.UiState.Empty -> {
                            updateEmptyState(true)
                        }
                        is HomeViewModel.UiState.Success -> {
                            updateEmptyState(false)
                        }
                        is HomeViewModel.UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        view?.let { view ->
            val emptyStateLayout = view.findViewById<LinearLayout>(R.id.emptyStateLayout)
            val recyclerView = view.findViewById<RecyclerView>(R.id.workoutCardsRecyclerView)

            if (isEmpty) {
                emptyStateLayout.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyStateLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun showCreateWorkoutCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_workout_card, null)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.workoutNameEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.workoutDescriptionEditText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Crea nuova scheda")
            .setView(dialogView)
            .setPositiveButton("Crea") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim().ifEmpty { null }

                if (name.isNotEmpty()) {
                    viewModel.createWorkoutCard(name, description)
                } else {
                    Toast.makeText(requireContext(), "Inserisci un nome per la scheda", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showWorkoutCardMenu(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(workoutCard.name)
            .setItems(arrayOf("Modifica", "Elimina")) { _, which ->
                when (which) {
                    0 -> {
                        // Modifica scheda
                        showEditWorkoutCardDialog(workoutCard)
                    }
                    1 -> {
                        // Elimina scheda
                        showDeleteConfirmationDialog(workoutCard)
                    }
                }
            }
            .show()
    }

    private fun showEditWorkoutCardDialog(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_workout_card, null)
        val nameEt = dialogView.findViewById<TextInputEditText>(R.id.workoutNameEditText)
        val descEt = dialogView.findViewById<TextInputEditText>(R.id.workoutDescriptionEditText)
        nameEt.setText(workoutCard.name)
        descEt.setText(workoutCard.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica scheda")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val name = nameEt.text.toString().trim()
                val desc = descEt.text.toString().trim().ifEmpty { null }
                if (name.isNotEmpty()) {
                    viewModel.updateWorkoutCard(workoutCard, name, desc)
                } else {
                    Toast.makeText(requireContext(), "Nome non può essere vuoto", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina scheda")
            .setMessage("Sei sicuro di voler eliminare \"${workoutCard.name}\"? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteWorkoutCard(workoutCard)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun checkForActiveWorkout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val activeSession = viewModel.getActiveWorkoutSession()
                if (activeSession != null) {
                    showActiveWorkoutBanner(activeSession)
                } else {
                    hideActiveWorkoutBanner()
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun showActiveWorkoutBanner(session: WorkoutSession) {
        activeWorkoutCard.visibility = View.VISIBLE
        
        // Update workout name in the banner
        val workoutNameText = activeWorkoutCard.findViewById<TextView>(R.id.activeWorkoutName)
        workoutNameText.text = session.name
        
        // Update timer if needed

    }

    private fun hideActiveWorkoutBanner() {
        activeWorkoutCard.visibility = View.GONE
    }

    private fun navigateToActiveWorkout() {
        viewLifecycleOwner.lifecycleScope.launch {
            val activeSession = viewModel.getActiveWorkoutSession() ?: return@launch
            
            // Use the correct action ID that matches your navigation graph
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToActiveWorkoutFragment(activeSession.workoutCardId)
            )
        }
    }


}