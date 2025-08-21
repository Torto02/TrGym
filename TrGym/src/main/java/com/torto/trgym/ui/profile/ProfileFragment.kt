
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
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.material.card.MaterialCardView
import com.torto.trgym.R
import com.torto.trgym.service.HeartRateDataListenerService
import com.torto.trgym.viewmodel.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    private var heartRateTextView: TextView? = null
    
    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("ProfileFragment", "BroadcastReceiver.onReceive called")
            Log.d("ProfileFragment", "Intent action: ${intent?.action}")
            Log.d("ProfileFragment", "Expected action: ${HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED}")
            
            if (intent?.action == HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED) {
                Log.d("ProfileFragment", "Heart rate broadcast received!")
                val heartRate = intent.getIntExtra(HeartRateDataListenerService.EXTRA_HEART_RATE, 0)
                val timestamp = intent.getLongExtra(HeartRateDataListenerService.EXTRA_TIMESTAMP, 0)
                Log.d("ProfileFragment", "Received heart rate: $heartRate, timestamp: $timestamp")
                updateHeartRateDisplay(heartRate, timestamp)
            } else {
                Log.w("ProfileFragment", "Received broadcast with wrong action: ${intent?.action}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
        
        // Registra il receiver per ricevere dati battito cardiaco
        val filter = IntentFilter(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED)
        Log.d("ProfileFragment", "Registering BroadcastReceiver with filter: ${HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED}")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(heartRateReceiver, filter)
        Log.d("ProfileFragment", "BroadcastReceiver registered successfully")
    }

    override fun onPause() {
        super.onPause()
        // Deregistra il receiver
        Log.d("ProfileFragment", "Unregistering BroadcastReceiver")
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(heartRateReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailTv = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)
        val textName = view.findViewById<TextView>(R.id.textName)
        val totalWorkouts = view.findViewById<TextView>(R.id.totalWorkouts)
        val workoutHistoryCard = view.findViewById<MaterialCardView>(R.id.cardWorkoutHistory)
        val resyncCard = view.findViewById<MaterialCardView>(R.id.btnResync)
        val bodyMeasurementsCard = view.findViewById<MaterialCardView>(R.id.cardBodyMeasurements)
        val wearOSCard = view.findViewById<MaterialCardView>(R.id.cardConnectWearOS)
        val wearOSStatus = view.findViewById<TextView>(R.id.wearOSStatus)
        heartRateTextView = view.findViewById<TextView>(R.id.heartRateStatus)

        setupClickListeners(logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
        observeUiState(emailTv, textName, totalWorkouts, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
    }

    private fun setupClickListeners(
        logoutCard: MaterialCardView,
        workoutHistoryCard: MaterialCardView,
        resyncCard: MaterialCardView,
        bodyMeasurementsCard: MaterialCardView,
        wearOSCard: MaterialCardView
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

        wearOSCard.setOnClickListener {
            checkWearOSConnection()
            testHeartRateService()
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
        wearOSCard: MaterialCardView
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            // Potresti mostrare un loading spinner qui
                        }

                        is ProfileViewModel.ProfileUiState.Syncing -> {
                            setCardsEnabled(false, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            Toast.makeText(requireContext(), "Sincronizzazione in corso...", Toast.LENGTH_SHORT).show()
                        }

                        is ProfileViewModel.ProfileUiState.Data -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                        }

                        is ProfileViewModel.ProfileUiState.SyncCompleted -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
                            updateProfileData(emailTv, textName, totalWorkouts, state.profileData)
                            Toast.makeText(requireContext(), "Sincronizzazione completata!", Toast.LENGTH_SHORT).show()


                        }

                        is ProfileViewModel.ProfileUiState.Error -> {
                            setCardsEnabled(true, logoutCard, workoutHistoryCard, resyncCard, bodyMeasurementsCard, wearOSCard)
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

    private fun checkWearOSConnection() {
        lifecycleScope.launch {
            try {
                val wearOSStatusTextView = view?.findViewById<TextView>(R.id.wearOSStatus)
                wearOSStatusTextView?.text = "Verifica in corso..."

                val connectedNodes = getConnectedWearOSDevices(requireContext())
                
                if (connectedNodes.isNotEmpty()) {
                    val deviceNames = connectedNodes.map { it.displayName }.joinToString(", ")
                    wearOSStatusTextView?.text = "Connesso: $deviceNames"
                    Toast.makeText(requireContext(), "Wear OS connesso: $deviceNames", Toast.LENGTH_LONG).show()
                    Log.d("WearOS", "Connected devices: $deviceNames")
                } else {
                    wearOSStatusTextView?.text = "Nessun dispositivo connesso"
                    Toast.makeText(requireContext(), "Nessun dispositivo Wear OS connesso", Toast.LENGTH_SHORT).show()
                    Log.d("WearOS", "No connected devices found")
                }
            } catch (e: Exception) {
                Log.e("WearOS", "Errore nella verifica connessione Wear OS", e)
                view?.findViewById<TextView>(R.id.wearOSStatus)?.text = "Errore nella verifica"
                Toast.makeText(requireContext(), "Errore nella verifica: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getConnectedWearOSDevices(context: Context): List<Node> {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            Log.d("WearOS", "Found ${nodes.size} connected nodes")
            nodes.toList()
        } catch (e: Exception) {
            Log.e("WearOS", "Error getting connected nodes", e)
            emptyList()
        }
    }

    private fun updateHeartRateDisplay(heartRate: Int, timestamp: Long) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = timeFormat.format(Date(timestamp))
        heartRateTextView?.text = "Battito: $heartRate BPM ($time)"
        Log.d("ProfileFragment", "Heart rate display updated: $heartRate BPM at $time")
    }
    
    private fun testHeartRateService() {
        Log.d("ProfileFragment", "Testing heart rate service with fake data")
        
        // Test con dati fake per verificare se il broadcast funziona
        val intent = Intent(HeartRateDataListenerService.ACTION_HEART_RATE_RECEIVED).apply {
            putExtra(HeartRateDataListenerService.EXTRA_HEART_RATE, 85)
            putExtra(HeartRateDataListenerService.EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        
        Log.d("ProfileFragment", "Sending test broadcast")
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }
}