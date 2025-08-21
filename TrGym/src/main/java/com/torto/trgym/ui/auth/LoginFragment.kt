package com.torto.trgym.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.torto.trgym.R
import com.torto.trgym.viewmodel.auth.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    // ViewModel per la gestione della logica di login
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Binding delle view (si consiglia l'uso di ViewBinding)
        val emailEt    = view.findViewById<EditText>(R.id.emailInput)
        val passwordEt = view.findViewById<EditText>(R.id.passwordInput)
        val loginBtn   = view.findViewById<Button>(R.id.loginBtn)
        val registerBtn= view.findViewById<Button>(R.id.registerBtn)
        val googleBtn  = view.findViewById<Button>(R.id.signInGoogleBtn)

        loginBtn.setOnClickListener {
            viewModel.login(
                email    = emailEt.text.toString().trim(),
                password = passwordEt.text.toString()
            )
        }

        // Listener per il pulsante di navigazione alla registrazione
        registerBtn.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        // Listener per il pulsante di login con Google
        googleBtn.setOnClickListener {
            viewModel.signInWithGoogle(requireContext())
        }

        // Osserva lo stato della UI dal ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LoginViewModel.LoginUiState.Idle -> {
                            loginBtn.isEnabled = true
                        }
                        is LoginViewModel.LoginUiState.Loading -> {
                            loginBtn.isEnabled = false
                        }
                        is LoginViewModel.LoginUiState.Success -> {
                            // Mostra il messaggio di successo
                            Toast.makeText(
                                requireContext(),
                                "Logged in successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Verifica se il frammento è ancora visibile e naviga solo se siamo nel loginFragment
                            if (isAdded && findNavController().currentDestination?.id == R.id.loginFragment) {
                                findNavController().navigate(R.id.action_login)
                            }
                        }
                        is LoginViewModel.LoginUiState.Error -> {
                            loginBtn.isEnabled = true
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