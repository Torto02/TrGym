
package com.torto.trgym.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.torto.trgym.R
import com.torto.trgym.services.HeartRateManager
import com.torto.trgym.viewmodel.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    
    @Inject
    lateinit var heartRateManager: HeartRateManager
    
    // Launcher per richiedere permessi
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateHeartRateUI()
            Toast.makeText(requireContext(), "Permessi concessi. Monitoraggio battito cardiaco abilitato!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Permessi necessari per il monitoraggio del battito cardiaco", Toast.LENGTH_LONG).show()
            heartRateManager.isConsentEnabled = false
            updateHeartRateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
        updateHeartRateUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailTv = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)
        val textName = view.findViewById<TextView>(R.id.textName)
        val totalWorkouts = view.findViewById<TextView>(R.id.totalWorkouts)
        val workoutHistoryCard = view.findViewById<MaterialCardView>(R.id.cardWorkoutHistory)
        val resyncCard = view.findViewById<MaterialCardView>(R.id.btnResync)
        val bodyMeasurementsCard = view.findViewById<MaterialCardView>(R.id.cardBodyMeasurements)
        
        // Elementi per battito cardiaco
        val heartRateCard = view.findViewById<MaterialCardView>(R.id.cardHeartRateConsent)
        val heartRateSwitch = view.findViewById<SwitchMaterial>(R.id.switchHeartRateConsent)
        val heartRateStatus = view.findViewById<TextView>(R.id.heartRateStatus)

        setupClickListeners(logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
        setupHeartRateListeners(heartRateCard, heartRateSwitch)
        observeUiState(emailTv, textName, totalWorkouts, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard)
        updateHeartRateUI()
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
    
    private fun setupHeartRateListeners(
        heartRateCard: MaterialCardView,
        heartRateSwitch: SwitchMaterial
    ) {
        // Click sulla card attiva/disattiva lo switch
        heartRateCard.setOnClickListener {
            heartRateSwitch.toggle()
        }
        
        // Gestione cambio stato switch
        heartRateSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Controllo permessi prima di abilitare
                if (heartRateManager.hasRequiredPermissions()) {
                    heartRateManager.isConsentEnabled = true
                    updateHeartRateUI()
                    Toast.makeText(requireContext(), "Monitoraggio battito cardiaco abilitato", Toast.LENGTH_SHORT).show()
                } else {
                    // Richiedi permessi
                    requestHeartRatePermissions()
                }
            } else {
                heartRateManager.isConsentEnabled = false
                updateHeartRateUI()
                Toast.makeText(requireContext(), "Monitoraggio battito cardiaco disabilitato", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateHeartRateUI() {
        val heartRateSwitch = view?.findViewById<SwitchMaterial>(R.id.switchHeartRateConsent)
        val heartRateStatus = view?.findViewById<TextView>(R.id.heartRateStatus)
        
        heartRateSwitch?.isChecked = heartRateManager.isConsentEnabled
        
        val statusText = when {
            !heartRateManager.hasRequiredPermissions() -> "Permessi necessari"
            heartRateManager.isConsentEnabled -> {
                val lastHR = heartRateManager.lastHeartRate
                if (lastHR > 0) "Attivo - Ultimo: ${lastHR} BPM" else "Attivo"
            }
            else -> "Disattivato"
        }
        
        heartRateStatus?.text = statusText
    }
    
    private fun requestHeartRatePermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        permissionLauncher.launch(permissions)
    }
}