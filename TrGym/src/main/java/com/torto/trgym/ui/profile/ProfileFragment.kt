
package com.torto.trgym.ui.profile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.torto.trgym.R
import com.torto.trgym.service.HeartRateDataListenerService
import com.torto.trgym.viewmodel.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()

    
    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            
            if (intent?.action == HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED) {
                val heartRate = intent.getIntExtra(HeartRateDataListenerService.EXTRA_HEART_RATE, 0)
                val timestamp = intent.getLongExtra(HeartRateDataListenerService.EXTRA_TIMESTAMP, 0)
                val heartRateStatus = view?.findViewById<TextView>(R.id.heartRateStatus)
                if (heartRateStatus != null) {
                    updateHeartRateDisplay(heartRate, timestamp, heartRateStatus)
                }
            } else {
                Log.w("ProfileFragment", "Received broadcast with wrong action: ${intent?.action}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Registra il receiver per ricevere dati battito cardiaco
        val filter = IntentFilter(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED)
        Log.d("ProfileFragment", "Registering BroadcastReceiver with filter: ${HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED}")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(heartRateReceiver, filter)
        Log.d("ProfileFragment", "BroadcastReceiver registered successfully")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailTv = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)
        val textName = view.findViewById<TextView>(R.id.textName)
        val totalWorkouts = view.findViewById<TextView>(R.id.totalWorkouts)
        val totalCalories = view.findViewById<TextView>(R.id.totalCalories)
        val workoutHistoryCard = view.findViewById<MaterialCardView>(R.id.cardWorkoutHistory)
        val resyncCard = view.findViewById<MaterialCardView>(R.id.btnResync)
        val bodyMeasurementsCard = view.findViewById<MaterialCardView>(R.id.cardBodyMeasurements)
        val wearOSCard = view.findViewById<MaterialCardView>(R.id.cardConnectWearOS)
        val heartRateStatus = view.findViewById<TextView>(R.id.heartRateStatus)

        setupClickListeners(logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
        observeUiState(emailTv, textName, totalWorkouts, totalCalories, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
    }

    private fun setupClickListeners(
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView,
        wearOSCard: MaterialCardView
    ) {
        logoutCard.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
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

        wearOSCard.setOnClickListener {
            checkWearOSConnection()
            testHeartRateService()
        }
    }

    private fun observeUiState(
        emailTv: TextView,
        textName: TextView,
        totalWorkouts: TextView,
        totalCalories: TextView,
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView,
        wearOSCard: MaterialCardView
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                        }

                        is ProfileViewModel.ProfileUiState.Syncing -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            Toast.makeText(requireContext(), "Synchronization...", Toast.LENGTH_SHORT).show()
                        }

                        is ProfileViewModel.ProfileUiState.Data -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            updateProfileData(emailTv, textName, totalWorkouts, totalCalories, state.profileData)
                        }

                        is ProfileViewModel.ProfileUiState.SyncCompleted -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            updateProfileData(emailTv, textName, totalWorkouts, totalCalories,state.profileData)
                            Toast.makeText(requireContext(), "Sync completed!", Toast.LENGTH_SHORT).show()
                        }

                        is ProfileViewModel.ProfileUiState.Error -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        }

                        is ProfileViewModel.ProfileUiState.LoggedOut -> {
                            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()

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
        totalCalories: TextView,
        profileData: ProfileViewModel.ProfileData
    ) {
        emailTv.text = profileData.email
        textName.text = profileData.displayName
        totalWorkouts.text = profileData.totalWorkouts.toString()
        totalCalories.text = profileData.totalCalories.toString() ?: "N/A"
    }

    private fun checkWearOSConnection() {
        lifecycleScope.launch {
            try {
                val wearOSStatusTextView = view?.findViewById<TextView>(R.id.wearOSStatus)
                val heartRateStatus = view?.findViewById<TextView>(R.id.heartRateStatus)  // Aggiungi questa riga

                wearOSStatusTextView?.text = "Check in progress..."

                val connectedNodes = getConnectedWearOSDevices(requireContext())
                
                if (connectedNodes.isNotEmpty()) {
                    val deviceNames = connectedNodes.map { it.displayName }.joinToString(", ")
                    wearOSStatusTextView?.text = "Connected: $deviceNames"
                    Toast.makeText(requireContext(), "Connected Wear OS: $deviceNames", Toast.LENGTH_LONG).show()
                    Log.d("WearOS", "Connected devices: $deviceNames")
                } else {
                    wearOSStatusTextView?.text = "No devices connected"
                    heartRateStatus?.text = "heartRate: N/A"
                    Toast.makeText(requireContext(), "No Wear Os Device connected", Toast.LENGTH_SHORT).show()
                    Log.d("WearOS", "No connected devices found")
                }
            } catch (e: Exception) {
                Log.e("WearOS", "Wear Os connection error", e)
                view?.findViewById<TextView>(R.id.wearOSStatus)?.text = "Error"
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getConnectedWearOSDevices(context: Context): List<Node> {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            nodes.toList()
        } catch (e: Exception) {
            Log.e("WearOS", "Error getting connected nodes", e)
            emptyList()
        }
    }

    private fun updateHeartRateDisplay(heartRate: Int, timestamp: Long, heartRateStatus: TextView) {
        heartRateStatus.text = "heartRate: $heartRate BPM"
    }
    
    private fun testHeartRateService() {

        val intent = Intent(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED).apply {
//            putExtra(HeartRateDataListenerService.EXTRA_HEART_RATE, 85)
            putExtra(HeartRateDataListenerService.EXTRA_TIMESTAMP, System.currentTimeMillis())
        }

        Log.d("ProfileFragment", "Sending test broadcast")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }
}