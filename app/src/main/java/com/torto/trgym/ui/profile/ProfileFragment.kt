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

/**
 * Fragment per la visualizzazione e gestione del profilo utente.
 * Permette di visualizzare i dati dell'utente e di effettuare il logout.
 * Utilizza Hilt per l'iniezione delle dipendenze.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    /**
     * ViewModel per la gestione dello stato e della logica del profilo.
     * Viene iniettato tramite Hilt con il delegato viewModels().
     */
    private val viewModel: ProfileViewModel by viewModels()

    /**
     * Configura le viste e gli observer dopo che la vista è stata creata.
     *
     * @param view La vista root del fragment
     * @param savedInstanceState Stato salvato del fragment
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Inizializzazione delle viste
        val emailTv    = view.findViewById<TextView>(R.id.UserEmail)
        val logoutCard = view.findViewById<MaterialCardView>(R.id.cardLogout)

        // Imposta il listener per l'azione di logout
        logoutCard.setOnClickListener {
            viewModel.logout()
        }

        // Osserva lo stato del profilo dal ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileViewModel.ProfileUiState.Loading -> {
                            // Disabilita il pulsante di logout durante il caricamento
                            logoutCard.isEnabled = false
                        }
                        is ProfileViewModel.ProfileUiState.Data -> {
                            // Mostra i dati dell'utente e abilita il pulsante di logout
                            logoutCard.isEnabled = true
                            emailTv.text = state.email
                        }
                        is ProfileViewModel.ProfileUiState.LoggedOut -> {
                            // Mostra un messaggio di logout effettuato
                            Toast.makeText(
                                requireContext(),
                                "Logout effettuato",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Verifica se il fragment è ancora visibile e se siamo nella schermata di profilo
                            // prima di tentare la navigazione
                            if (isAdded && findNavController().currentDestination?.id == R.id.profileFragment) {
                                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                            } else if (findNavController().currentDestination?.id != R.id.loginFragment) {
                                // Se non siamo nel loginFragment ma in un'altra schermata, naviga direttamente al login
                                findNavController().navigate(R.id.loginFragment)
                            }
                            // Se siamo già nel loginFragment, non facciamo nulla
                        }
                    }
                }
            }
        }
    }
}