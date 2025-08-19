package com.torto.trgym.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.torto.trgym.R
import com.torto.trgym.viewmodel.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()

    // Rileva se siamo in emulatore
    private val isEmulator: Boolean by lazy {
        (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("goldfish")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator"))
    }

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
        val heartRateMonitorCard = view.findViewById<MaterialCardView>(R.id.cardHeartRateMonitor)

        Log.d("ProfileFragment", "Ambiente rilevato: ${if (isEmulator) "Emulatore" else "Dispositivo fisico"}")

        setupClickListeners(logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
        observeUiState(emailTv, textName, totalWorkouts, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
    }

    private fun setupClickListeners(
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView,
        heartRateMonitorCard: MaterialCardView
    ) {
        logoutCard.setOnClickListener {
            viewModel.logout()
        }

        heartRateMonitorCard.setOnClickListener {
            if (isEmulator) {
                // In emulatore, vai direttamente al fragment senza controllo permessi
                Log.d("ProfileFragment", "Emulatore rilevato - skip controllo permessi")
                navigateToHeartRateMonitor()
            } else {
                // Su dispositivo fisico, controlla i permessi
                checkAndRequestHeartRatePermissions()
            }
        }

        workoutHistoryCard.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_workoutHistoryFragment)
        }

        resyncCard.setOnClickListener {
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
        bodyMeasurementsCard: MaterialCardView,
        heartRateMonitorCard: MaterialCardView
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
                        }

                        is ProfileViewModel.ProfileUiState.Syncing -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
                            Toast.makeText(requireContext(), "Sincronizzazione in corso...", Toast.LENGTH_SHORT).show()
                        }

                        is ProfileViewModel.ProfileUiState.Data -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                        }

                        is ProfileViewModel.ProfileUiState.SyncCompleted -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                            Toast.makeText(requireContext(), "Sincronizzazione completata!", Toast.LENGTH_SHORT).show()

                            view?.postDelayed({
                                if (isAdded && findNavController().currentDestination?.id == R.id.profileFragment) {
                                    findNavController().navigate(R.id.action_profileFragment_to_homeFragment)
                                }
                            }, 1500)
                        }

                        is ProfileViewModel.ProfileUiState.Error -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, heartRateMonitorCard)
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

    private fun checkAndRequestHeartRatePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Body Sensors - sempre necessario
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }

        // Bluetooth permissions solo per dispositivi fisici
        if (!isEmulator) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                }
            } else {
                // Android 11 and below
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH)
                }
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }
        }

        Log.d("ProfileFragment", "Permessi da richiedere: $permissionsToRequest")

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            navigateToHeartRateMonitor()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }

        Log.d("ProfileFragment", "Risultato permessi: $permissions")

        if (allGranted) {
            navigateToHeartRateMonitor()
        } else {
            // In emulatore, procedi comunque se mancano solo i permessi Bluetooth
            if (isEmulator) {
                val deniedPermissions = permissions.filter { !it.value }.keys
                val onlyBluetoothDenied = deniedPermissions.all {
                    it.contains("BLUETOOTH", ignoreCase = true)
                }

                if (onlyBluetoothDenied) {
                    Log.d("ProfileFragment", "Solo permessi Bluetooth negati in emulatore, procedo comunque")
                    Toast.makeText(
                        requireContext(),
                        "Procedo senza Bluetooth (modalità emulatore)",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToHeartRateMonitor()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Permesso BODY_SENSORS necessario",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permessi necessari per il monitoraggio del battito cardiaco",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToHeartRateMonitor() {
        Log.d("ProfileFragment", "Navigazione a HeartRateFragment")
        findNavController().navigate(R.id.action_profileFragment_to_heartRateFragment)
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