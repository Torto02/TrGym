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

@AndroidEntryPoint
class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailEt       = view.findViewById<EditText>(R.id.emailInput)
        val usernameEt    = view.findViewById<EditText>(R.id.usernameInput)
        val passwordEt    = view.findViewById<EditText>(R.id.passwordInput)
        val confirmEt     = view.findViewById<EditText>(R.id.checkPasswordInput)
        val registerBtn   = view.findViewById<Button>(R.id.registerBtn)
        val googleBtn     = view.findViewById<Button>(R.id.signInGoogleBtn)
        val loginBtn      = view.findViewById<Button>(R.id.loginBtn)


        registerBtn.setOnClickListener {
            viewModel.register(
                email    = emailEt.text.toString().trim(),
                username = usernameEt.text.toString().trim(),
                password = passwordEt.text.toString(),
                confirm  = confirmEt.text.toString()
            )
        }

        googleBtn.setOnClickListener {
            viewModel.signInWithGoogle(requireContext())
        }

        loginBtn.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        // Osserva lo stato della UI dal ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is RegisterViewModel.RegisterUiState.Idle -> {
                            registerBtn.isEnabled = true
                        }
                        is RegisterViewModel.RegisterUiState.Loading -> {
                            registerBtn.isEnabled = false
                        }
                        is RegisterViewModel.RegisterUiState.Success -> {
                            Toast.makeText(
                                requireContext(),
                                "Registration complete! Log in to enter the app..",
                                Toast.LENGTH_LONG
                            ).show()
                            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                        }
                        is RegisterViewModel.RegisterUiState.Error -> {
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