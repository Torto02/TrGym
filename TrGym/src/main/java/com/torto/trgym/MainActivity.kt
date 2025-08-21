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
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.activity.viewModels
import com.torto.trgym.viewmodel.exercises.ExercisesViewModel

/**
 * MainActivity serves as the entry point of the application after the splash screen.
 * It handles navigation, authentication status observation, and data synchronization.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Injected repositories for authentication, exercises, and workouts
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var exerciseRepo: ExerciseRepository
    @Inject lateinit var workoutRepo: WorkoutRepository

    // ViewModel for managing exercise-related data
    private val exercisesViewModel: ExercisesViewModel by viewModels()

    // Navigation controller for managing navigation between fragments
    private lateinit var navController: NavController

    // Bottom navigation view for navigating between main sections
    private lateinit var bottomNavigationView: BottomNavigationView

    // Flag to determine if authentication has been resolved
    private var isAuthenticationResolved = false

    /**
     * Called when the activity is created. Sets up the splash screen, navigation, and authentication observation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen and keep it visible until authentication is resolved
        installSplashScreen()
            .setKeepOnScreenCondition { !isAuthenticationResolved }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the ViewModel
        exercisesViewModel

        // Handle deep links for authentication
        authRepository.handleDeeplinks(intent)

        // Set up navigation and observe authentication status
        setupNavigation()
        observeAuthenticationStatus()
    }

    /**
     * Sets up the navigation controller and bottom navigation view.
     */
    private fun setupNavigation() {
        // Retrieve the navigation host fragment and its controller
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.setGraph(R.navigation.nav_graph)

        // Initialize the bottom navigation view and set it to be initially hidden
        bottomNavigationView = findViewById(R.id.bottom_nav)
        bottomNavigationView.visibility = View.GONE

        // Handle bottom navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            val builder = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(item.itemId != R.id.workoutDetailFragment)

            // Configure navigation options based on the selected item
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

        // Update bottom navigation visibility and selection based on the current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigationView.visibility = when (destination.id) {
                R.id.loginFragment, R.id.registerFragment -> View.GONE
                else -> View.VISIBLE
            }
            bottomNavigationView.menu.findItem(destination.id)?.isChecked = true
        }
    }

    /**
     * Downloads the initial set of starred exercises from the repository.
     */
    private fun downloadStarredExercisesInitial() {
        lifecycleScope.launch {
            try {
                exerciseRepo.downloadStarredExercisesInitial()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error downloading starred exercises initially", e)
            }
        }
    }

    /**
     * Downloads the initial set of workout cards from the repository.
     */
    private fun downloadWorkoutCardsInitial() {
        lifecycleScope.launch {
            try {
                workoutRepo.downloadWorkoutCardsInitial()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error downloading workout cards initially", e)
            }
        }
    }

    /**
     * Uploads any pending workout sessions to the repository.
     */
    private fun uploadPendingWorkoutSessions() {
        lifecycleScope.launch {
            try {
                // Retrieve the active workout session
                val activeSession = workoutRepo.getActiveWorkoutSession()
                if (activeSession != null) {
                    // Update the session and its associated exercises and sets
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

    /**
     * Observes the authentication status and updates the UI and navigation accordingly.
     */
    private fun observeAuthenticationStatus() {
        lifecycleScope.launch {
            authRepository.observeAuthStatus().collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Perform initial data synchronization
                        downloadStarredExercisesInitial()
                        downloadWorkoutCardsInitial()
                        uploadPendingWorkoutSessions()

                        // Navigate to the home fragment if currently on login or register
                        navController.currentDestination?.id.let { current ->
                            if (current == R.id.loginFragment || current == R.id.registerFragment) {
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                                navController.navigate(R.id.homeFragment, null, navOptions)
                            }
                        }
                        isAuthenticationResolved = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        // Navigate to the login fragment
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                        navController.navigate(R.id.loginFragment, null, navOptions)
                        isAuthenticationResolved = true
                    }
                    else -> {
                        // Handle other authentication states if necessary
                    }
                }
            }
        }
    }

    /**
     * Handles new intents, such as deep links, and passes them to the authentication repository.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authRepository.handleDeeplinks(intent)
    }
}