
package com.torto.trgym.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.viewmodel.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailTv = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)
        val textName = view.findViewById<TextView>(R.id.textName)
        val totalWorkouts = view.findViewById<TextView>(R.id.totalWorkouts)
        val workoutHistoryCard = view.findViewById<MaterialCardView>(R.id.cardWorkoutHistory)
        val resyncCard = view.findViewById<MaterialCardView>(R.id.btnResync)
        val bodyMeasurementsCard = view.findViewById<MaterialCardView>(R.id.cardBodyMeasurements)

        setupClickListeners(logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
        observeUiState(emailTv, textName, totalWorkouts, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
    }

    private fun setupClickListeners(
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView
    ) {
        logoutCard.setOnClickListener {
            viewModel.logout()
        }

        workoutHistoryCard.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_workoutHistoryFragment)
        }

        resyncCard.setOnClickListener {
            // Avvia la sincronizzazione completa
            viewModel.syncWithSupabase()
        }

        bodyMeasurementsCard.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_bodyMeasurementsFragment)
        }
    }

    private fun observeUiState(
        emailTv: TextView,
        textName: TextView,
        totalWorkouts: TextView,
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
                            // Potresti mostrare un loading spinner qui
                        }

                        is ProfileViewModel.ProfileUiState.Syncing -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
                            Toast.makeText(requireContext(), "Sincronizzazione in corso...", Toast.LENGTH_SHORT).show()
                        }

                        is ProfileViewModel.ProfileUiState.Data -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                        }

                        is ProfileViewModel.ProfileUiState.SyncCompleted -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                            Toast.makeText(requireContext(), "Sincronizzazione completata!", Toast.LENGTH_SHORT).show()


                        }

                        is ProfileViewModel.ProfileUiState.Error -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }

                        is ProfileViewModel.ProfileUiState.LoggedOut -> {
                            Toast.makeText(requireContext(), "Logout effettuato", Toast.LENGTH_SHORT).show()

                            if (isAdded && findNavController().currentDestination?.id == R.id.profileFragment) {
                                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                            } else if (findNavController().currentDestination?.id != R.id.loginFragment) {
                                findNavController().navigate(R.id.loginFragment)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setCardsEnabled(
        enabled: Boolean,
        vararg cards: MaterialCardView
    ) {
        cards.forEach { it.isEnabled = enabled }
    }

    private fun updateProfileData(
        emailTv: TextView,
        textName: TextView,
        totalWorkouts: TextView,
        profileData: ProfileViewModel.ProfileData
    ) {
        emailTv.text = profileData.email
        textName.text = profileData.displayName
        totalWorkouts.text = profileData.totalWorkouts.toString()
    }
}