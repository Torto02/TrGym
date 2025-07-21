package com.torto.trgym

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torto.trgym.R
import com.torto.trgym.data.repository.AuthRepository
//import com.torto.trgym.ui.viewmodel.ExercisesViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Inietto AuthRepository tramite Hilt
    @Inject lateinit var authRepository: AuthRepository

    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    // ExercisesViewModel già iniettato con Hilt
//    private val exercisesViewModel: ExercisesViewModel by viewModels()

    // Controllo la splash finché non risolvo l'autenticazione
    private var isAuthenticationResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
            .setKeepOnScreenCondition { !isAuthenticationResolved }

        // Gestisco eventuali deep link di Supabase


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository.handleDeeplinks(intent)

        setupNavigation()
        observeAuthenticationStatus()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.setGraph(R.navigation.nav_graph)

        bottomNavigationView = findViewById(R.id.bottom_nav)
        bottomNavigationView.visibility = View.GONE

        bottomNavigationView.setOnItemSelectedListener { item ->
            val builder = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setEnterAnim(0).setExitAnim(0)
                .setPopEnterAnim(0).setPopExitAnim(0)

            if (item.order and Menu.CATEGORY_SECONDARY == 0) {
                builder.setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = true
                )
            }

            val options = builder.build()
            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigationView.visibility = when (destination.id) {
                R.id.loginFragment, R.id.registerFragment -> View.GONE
                else -> View.VISIBLE
            }
            bottomNavigationView.menu.findItem(destination.id)?.isChecked = true
        }
    }

    private fun observeAuthenticationStatus() {
        lifecycleScope.launch {
            authRepository.observeAuthStatus().collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        navController.currentDestination?.id.let { current ->
                            if (current == R.id.loginFragment || current == R.id.registerFragment) {
                                navController.navigate(R.id.homeFragment)
                            }
                        }
//                        exercisesViewModel.preloadIfNeeded()
                        isAuthenticationResolved = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        navController.navigate(R.id.loginFragment)
                        isAuthenticationResolved = true
                    }
                    else -> {
                        // altri stati (Restored, etc.)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authRepository.handleDeeplinks(intent)
    }
}
