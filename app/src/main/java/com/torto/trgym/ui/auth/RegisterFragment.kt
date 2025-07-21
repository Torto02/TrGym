package com.torto.trgym.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.torto.trgym.R
import dagger.hilt.android.AndroidEntryPoint
import com.torto.trgym.viewmodel.auth.RegisterViewModel
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch

/**
 * Fragment per la registrazione degli utenti.
 * Utilizza Hilt per l'iniezione delle dipendenze.
 */
@AndroidEntryPoint
class RegisterFragment : Fragment(R.layout.fragment_register) {

    // ViewModel per la gestione della logica di registrazione
    private val viewModel: RegisterViewModel by viewModels()

    /**
     * Metodo chiamato quando la vista è stata creata.
     * Effettua il binding delle view e imposta i listener per i pulsanti.
     *
     * @param view Vista principale del fragment
     * @param savedInstanceState Stato salvato del fragment
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Binding delle view (si consiglia l'uso di ViewBinding)
        val emailEt       = view.findViewById<EditText>(R.id.emailInput) // Campo di input per l'email
        val usernameEt    = view.findViewById<EditText>(R.id.usernameInput) // Campo di input per il nome utente
        val passwordEt    = view.findViewById<EditText>(R.id.passwordInput) // Campo di input per la password
        val confirmEt     = view.findViewById<EditText>(R.id.checkPasswordInput) // Campo di input per la conferma della password
        val registerBtn   = view.findViewById<Button>(R.id.registerBtn) // Pulsante per la registrazione
        val googleBtn     = view.findViewById<Button>(R.id.signInGoogleBtn) // Pulsante per il login con Google
        val loginBtn      = view.findViewById<Button>(R.id.loginBtn) // Pulsante per navigare al login

        // Listener per il pulsante di registrazione
        registerBtn.setOnClickListener {
            viewModel.register(
                email    = emailEt.text.toString().trim(),
                username = usernameEt.text.toString().trim(),
                password = passwordEt.text.toString(),
                confirm  = confirmEt.text.toString()
            )
        }

        // Listener per il pulsante di login con Google
        googleBtn.setOnClickListener {
            viewModel.signInWithGoogle(requireContext())
        }

        // Listener per il pulsante di navigazione al login
        loginBtn.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        // Osserva lo stato della UI dal ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is RegisterViewModel.RegisterUiState.Idle -> {
                            // Stato iniziale: abilita il pulsante di registrazione
                            registerBtn.isEnabled = true
                        }
                        is RegisterViewModel.RegisterUiState.Loading -> {
                            // Stato di caricamento: disabilita il pulsante di registrazione
                            registerBtn.isEnabled = false
                        }
                        is RegisterViewModel.RegisterUiState.Success -> {
                            // Stato di successo: mostra un messaggio e naviga al login
                            Toast.makeText(
                                requireContext(),
                                "Registrazione avvenuta! Controlla la tua email.",
                                Toast.LENGTH_LONG
                            ).show()
                            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                        }
                        is RegisterViewModel.RegisterUiState.Error -> {
                            // Stato di errore: abilita il pulsante e mostra un messaggio di errore
                            registerBtn.isEnabled = true
                            Toast.makeText(
                                requireContext(),
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}