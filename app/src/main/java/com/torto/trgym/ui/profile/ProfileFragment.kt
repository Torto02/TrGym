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

    // In ProfileFragment.kt
    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailTv    = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)
        val textName = view.findViewById<TextView>(R.id.textName)
        val totalWorkouts= view.findViewById<TextView>(R.id.totalWorkouts)


        logoutCard.setOnClickListener {
            viewModel.logout()
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            logoutCard.isEnabled = false
                        }
                        is ProfileViewModel.ProfileUiState.Data -> {
                            logoutCard.isEnabled = true
                            emailTv.text = state.profileData.email
                            textName.text = state.profileData.displayName
                            totalWorkouts.text = state.profileData.totalWorkouts.toString()

                        }
                        is ProfileViewModel.ProfileUiState.Error -> {
                            Toast.makeText(
                                requireContext(),
                                state.message,
                                Toast.LENGTH_SHORT
                            ).show()
                            logoutCard.isEnabled = true
                        }
                        is ProfileViewModel.ProfileUiState.LoggedOut -> {
                            Toast.makeText(
                                requireContext(),
                                "Logout effettuato",
                                Toast.LENGTH_SHORT
                            ).show()

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
}