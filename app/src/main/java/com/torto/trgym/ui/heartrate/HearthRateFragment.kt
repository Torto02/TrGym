package com.torto.trgym.ui.heartrate

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.torto.trgym.R
import com.torto.trgym.viewmodel.heartrate.HeartRateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HeartRateFragment : Fragment(R.layout.fragment_heart_rate) {

    private val viewModel: HeartRateViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val heartRateText = view.findViewById<TextView>(R.id.tvHeartRate)
        val caloriesText = view.findViewById<TextView>(R.id.tvCaloriesBurned)
        val progressIndicator = view.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val weightInput = view.findViewById<EditText>(R.id.etWeight)
        val heightInput = view.findViewById<EditText>(R.id.etHeight)
        val startStopButton = view.findViewById<Button>(R.id.btnStartStop)
        val statusText = view.findViewById<TextView>(R.id.tvStatus)

        // Carica i valori salvati di altezza e peso se disponibili
        viewModel.getUserMeasurements()?.let { measurements ->
            weightInput.setText(measurements.weight.toString())
            heightInput.setText(measurements.height.toString())
        }

        startStopButton.setOnClickListener {
            if (viewModel.isMonitoring()) {
                viewModel.stopMonitoring()
                startStopButton.text = "Avvia Monitoraggio"
            } else {
                val weight = weightInput.text.toString().toFloatOrNull()
                val height = heightInput.text.toString().toFloatOrNull()

                if (weight != null && height != null) {
                    viewModel.startMonitoring(weight, height)
                    startStopButton.text = "Ferma Monitoraggio"

                    // Salva i valori per uso futuro
                    viewModel.saveUserMeasurements(weight, height)
                } else {
                    statusText.text = "Inserisci peso (kg) e altezza (cm) validi"
                }
            }
        }

        // Osserva i cambiamenti nello stato
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.heartRateState.collect { state ->
                    when (state) {
                        is HeartRateViewModel.HeartRateState.Connecting -> {
                            statusText.text = "Connessione al dispositivo Wear OS..."
                            progressIndicator.visibility = View.VISIBLE
                            heartRateText.text = "--"
                            caloriesText.text = "0"
                        }

                        is HeartRateViewModel.HeartRateState.Monitoring -> {
                            statusText.text = "Connesso e monitoraggio attivo"
                            progressIndicator.visibility = View.VISIBLE
                            heartRateText.text = state.heartRate.toString()
                            caloriesText.text = state.calories.toString()

                            // Aggiorna anche il progresso del cerchio (0-220 bpm)
                            val progress = (state.heartRate.toFloat() / 220f * 100f).toInt()
                            progressIndicator.progress = progress.coerceIn(0, 100)
                        }

                        is HeartRateViewModel.HeartRateState.Error -> {
                            statusText.text = "Errore: ${state.message}"
                            progressIndicator.visibility = View.INVISIBLE
                            startStopButton.text = "Riprova"
                        }

                        is HeartRateViewModel.HeartRateState.Idle -> {
                            statusText.text = "Pronto per iniziare il monitoraggio"
                            progressIndicator.visibility = View.INVISIBLE
                            heartRateText.text = "--"
                            caloriesText.text = "0"
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isMonitoring()) {
            viewModel.stopMonitoring()
            view?.findViewById<Button>(R.id.btnStartStop)?.text = "Avvia Monitoraggio"
        }
    }
}