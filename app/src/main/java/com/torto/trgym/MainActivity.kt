package com.torto.trgym

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torto.trgym.data.repository.AuthRepository
import com.torto.trgym.data.repository.ExerciseRepository
import com.torto.trgym.data.repository.WorkoutRepository
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.activity.viewModels
import com.torto.trgym.viewmodel.exercises.ExercisesViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Inietto AuthRepository tramite Hilt
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var exerciseRepo: ExerciseRepository
    @Inject lateinit var workoutRepo: WorkoutRepository

    private val exercisesViewModel: ExercisesViewModel by viewModels()
    

    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    // Controllo la splash finché non risolvo l'autenticazione
    private var isAuthenticationResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
            .setKeepOnScreenCondition { !isAuthenticationResolved }

        // Gestisco eventuali deep link di Supabase


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Pre‐carica fin da subito gli esercizi nella cache di Room
        exercisesViewModel

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
                // MODIFICATO: Non ripristinare lo stato per workoutDetailFragment
                .setRestoreState(item.itemId != R.id.workoutDetailFragment)

            if (item.order and Menu.CATEGORY_SECONDARY == 0) {
                builder.setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = item.itemId != R.id.workoutDetailFragment
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

    private fun downloadStarredExercisesInitial() {
        lifecycleScope.launch {
            try {
                exerciseRepo.downloadStarredExercisesInitial()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error downloading starred exercises initially", e)
            }
        }
    }

    private fun downloadWorkoutCardsInitial() {
        lifecycleScope.launch {
            try {
                workoutRepo.downloadWorkoutCardsInitial()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error downloading workout cards initially", e)
            }
        }
    }

    private fun uploadPendingWorkoutSessions() {
        lifecycleScope.launch {
            try {
                // Upload pending changes for active and recent workout sessions - SOLO PUSH
                val activeSession = workoutRepo.getActiveWorkoutSession()
                if (activeSession != null) {
                    // Upload the active session and all its exercises and sets to Supabase
                    workoutRepo.updateWorkoutSession(activeSession)
                    
                    val exercises = workoutRepo.getSessionExercises(activeSession.id)
                    exercises.forEach { exercise ->
                        workoutRepo.updateSessionExercise(exercise)
                        val sets = workoutRepo.getSessionSets(exercise.id)
                        sets.forEach { set ->
                            workoutRepo.updateSessionSet(set)
                        }
                    }
                }
                Log.d("MainActivity", "Upload of pending workout sessions completed")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error uploading pending workout sessions", e)
            }
        }
    }

    private fun observeAuthenticationStatus() {
        lifecycleScope.launch {
            authRepository.observeAuthStatus().collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // DOWNLOAD INIZIALE - fatto solo al primo accesso dell'app
                        downloadStarredExercisesInitial()
                        downloadWorkoutCardsInitial()
                        uploadPendingWorkoutSessions() // Upload eventuali modifiche pendenti
                        
                        navController.currentDestination?.id.let { current ->
                            if (current == R.id.loginFragment || current == R.id.registerFragment) {
                                navController.navigate(R.id.homeFragment)
                            }
                        }
                        isAuthenticationResolved = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        navController.navigate(R.id.loginFragment)
                        isAuthenticationResolved = true
                    }
                    else -> {
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authRepository.handleDeeplinks(intent)
    }

    //non penso sia necessario, ma lo lascio per sicurezza
    private fun preloadExercises() {
        lifecycleScope.launch {
            exerciseRepo
                .allExercises()          // Flow<List<ExerciseWithImage>>
                .first()                 // sospende finché la prima lista non arriva
        }
    }
}