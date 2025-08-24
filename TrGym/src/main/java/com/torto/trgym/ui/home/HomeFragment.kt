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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.torto.trgym.R
import com.torto.trgym.adapters.WorkoutCardsAdapter
import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.entities.WorkoutSession
import com.torto.trgym.viewmodel.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var workoutCardsAdapter: WorkoutCardsAdapter
    private lateinit var activeWorkoutCard: MaterialCardView
    private lateinit var activeWorkoutTime: TextView
    private lateinit var continueWorkoutBtn: MaterialButton
    private lateinit var minutes: TextView
    private lateinit var activeWorkoutStatus: TextView
    private lateinit var workoutCardsSubtitle: TextView
    private lateinit var createFirstWorkoutBtn: MaterialButton

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
        val addWorkoutCardBtn = view.findViewById<MaterialButton>(R.id.addWorkoutCardBtn)
        activeWorkoutCard = view.findViewById(R.id.activeWorkoutCard)
        activeWorkoutTime = view.findViewById(R.id.activeWorkoutTime)
        continueWorkoutBtn = view.findViewById(R.id.continueWorkoutBtn)
        minutes = view.findViewById(R.id.minutes)
        activeWorkoutStatus = view.findViewById(R.id.activeWorkoutStatus)
        workoutCardsSubtitle = view.findViewById(R.id.workoutCardsSubtitle)
        createFirstWorkoutBtn = view.findViewById(R.id.createFirstWorkoutBtn)


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

                    workoutCardsSubtitle.text = "${workoutCards.size} workout cards"
                    updateEmptyState(workoutCards.isEmpty())
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
                createFirstWorkoutBtn.setOnClickListener { showCreateWorkoutCardDialog() }
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

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Create a new workout card")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim().ifEmpty { null }

                if (name.isNotEmpty()) {
                    viewModel.createWorkoutCard(name, description)
                } else {
                    Toast.makeText(requireContext(), "Enter a name for the card.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWorkoutCardMenu(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(workoutCard.name)
            .setItems(arrayOf("Delete")) { _, which ->
                when (which) {

                    0 -> {
                        showDeleteConfirmationDialog(workoutCard)
                    }
                }
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Delete Workout Card")
            .setMessage("Are you sure you want to delete \"${workoutCard.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteWorkoutCard(workoutCard)
            }
            .setNegativeButton("Cancel", null)
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
                //
            }
        }
    }

    private var timerJob: Job? = null

    private fun showActiveWorkoutBanner(session: WorkoutSession) {
        activeWorkoutCard.visibility = View.VISIBLE
        val workoutNameText = activeWorkoutCard.findViewById<TextView>(R.id.activeWorkoutName)
        workoutNameText.text = session.name

        timerJob?.cancel()
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val startTime = session.startTime
                val elapsedTime = System.currentTimeMillis() - startTime
                activeWorkoutTime.text = formatElapsedTime(elapsedTime)
                delay(1000)
            }
        }
    }

    private fun hideActiveWorkoutBanner() {
        timerJob?.cancel()
        activeWorkoutCard.visibility = View.VISIBLE
        val workoutNameText = activeWorkoutCard.findViewById<TextView>(R.id.activeWorkoutName)
        workoutNameText.text = "No workout in progress  \nOpen a card to start a workout"
        activeWorkoutTime.visibility = View.GONE
        continueWorkoutBtn.visibility = View.GONE
        minutes.visibility= View.GONE
        activeWorkoutStatus.visibility = View.GONE
    }

    private fun navigateToActiveWorkout() {
        viewLifecycleOwner.lifecycleScope.launch {
            val activeSession = viewModel.getActiveWorkoutSession() ?: return@launch
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToActiveWorkoutFragment(activeSession.workoutCardId)
            )
        }
    }


    private fun formatElapsedTime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}