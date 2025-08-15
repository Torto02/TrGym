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
        val freeWorkoutCard = view.findViewById<MaterialCardView>(R.id.freeWorkoutCard)
        val addWorkoutCardBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.addWorkoutCardBtn)
        activeWorkoutCard = view.findViewById(R.id.activeWorkoutCard)

        freeWorkoutCard.setOnClickListener {
            // Naviga alla schermata di allenamento libero
            findNavController().navigate(R.id.action_home_to_freeWorkout)
        }

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
                        // TODO: Implementare modifica
                    }
                    1 -> {
                        // Elimina scheda
                        showDeleteConfirmationDialog(workoutCard)
                    }
                }
            }
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
        val timerText = activeWorkoutCard.findViewById<TextView>(R.id.activeWorkoutTimer)
        val startTime = session.startTime
        val elapsedTime = System.currentTimeMillis() - startTime
        timerText.text = formatElapsedTime(elapsedTime)
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

    private fun formatElapsedTime(elapsedMillis: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}