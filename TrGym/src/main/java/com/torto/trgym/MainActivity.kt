package com.torto.trgym

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torto.trgym.repository.AuthRepository
import com.torto.trgym.repository.ExerciseRepository
import com.torto.trgym.repository.WorkoutRepository
import com.torto.trgym.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainActivity serves as the entry point of the application after the splash screen.
 * It handles navigation, authentication status observation, and data synchronization.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var exerciseRepo: ExerciseRepository
    @Inject lateinit var workoutRepo: WorkoutRepository
    @Inject lateinit var preferencesManager: PreferencesManager
    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView
    private var isAuthenticationResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
            .setKeepOnScreenCondition { !isAuthenticationResolved }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository.handleDeeplinks(intent)

        setupNavigation()
        checkOfflineLoginState()
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

        bottomNavigationView.setOnItemSelectedListener { item ->
            navigateWithBottomNavAnimations(item.itemId)
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigationView.visibility = when (destination.id) {
                R.id.loginFragment, R.id.registerFragment -> View.GONE
                else -> View.VISIBLE
            }
            bottomNavigationView.menu.findItem(destination.id)?.isChecked = true
        }
    }

    /**
     * Handles navigation from bottom navigation with appropriate animations.
     */
    private fun navigateWithBottomNavAnimations(destinationId: Int) {
        val currentDestinationId = navController.currentDestination?.id

        // Skip navigation if already on the same destination
        if (currentDestinationId == destinationId) {
            return
        }

        val navOptionsBuilder = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(destinationId != R.id.workoutDetailFragment)

        // Set appropriate animations based on navigation direction
        when (// From home to other tabs
            currentDestinationId) {
            R.id.homeFragment -> {
                when (destinationId) {
                    R.id.exercisesFragment, R.id.profileFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_up)
                            .setExitAnim(R.anim.fade_out)
                            .setPopEnterAnim(R.anim.fade_in)
                            .setPopExitAnim(R.anim.slide_out_down)
                    }
                }
            }
            // From exercises to home or profile
            R.id.exercisesFragment -> {
                when (destinationId) {
                    R.id.homeFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.fade_in)
                            .setExitAnim(R.anim.slide_out_down)
                            .setPopEnterAnim(R.anim.slide_in_up)
                            .setPopExitAnim(R.anim.fade_out)
                    }

                    R.id.profileFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_right)
                            .setExitAnim(R.anim.slide_out_left)
                            .setPopEnterAnim(R.anim.slide_in_left)
                            .setPopExitAnim(R.anim.slide_out_right)
                    }
                }
            }
            // From profile to home or exercises
            R.id.profileFragment -> {
                when (destinationId) {
                    R.id.homeFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.fade_in)
                            .setExitAnim(R.anim.slide_out_down)
                            .setPopEnterAnim(R.anim.slide_in_up)
                            .setPopExitAnim(R.anim.fade_out)
                    }

                    R.id.exercisesFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left)
                    }
                }
            }
            // From detail fragments to main tabs (same animations as their "parent" fragments)
            R.id.workoutDetailFragment, R.id.activeWorkoutFragment -> {
                when (destinationId) {
                    R.id.homeFragment -> {
                        // Same as exercises -> home (since workouts are typically accessed from home)
                        navOptionsBuilder
                            .setEnterAnim(R.anim.fade_in)
                            .setExitAnim(R.anim.slide_out_down)
                            .setPopEnterAnim(R.anim.slide_in_up)
                            .setPopExitAnim(R.anim.fade_out)
                    }

                    R.id.exercisesFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_up)
                            .setExitAnim(R.anim.fade_out)
                            .setPopEnterAnim(R.anim.fade_in)
                            .setPopExitAnim(R.anim.slide_out_down)
                    }

                    R.id.profileFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_up)
                            .setExitAnim(R.anim.fade_out)
                            .setPopEnterAnim(R.anim.fade_in)
                            .setPopExitAnim(R.anim.slide_out_down)
                    }
                }
            }

            R.id.exerciseDetailFragment -> {
                when (destinationId) {
                    R.id.homeFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.fade_in)
                            .setExitAnim(R.anim.slide_out_down)
                            .setPopEnterAnim(R.anim.slide_in_up)
                            .setPopExitAnim(R.anim.fade_out)
                    }

                    R.id.exercisesFragment -> {
                        // Same as exercises -> exercises (stay put, but with subtle transition)
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left)
                    }

                    R.id.profileFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_right)
                            .setExitAnim(R.anim.slide_out_left)
                            .setPopEnterAnim(R.anim.slide_in_left)
                            .setPopExitAnim(R.anim.slide_out_right)
                    }
                }
            }
            // From profile-related fragments (history, measurements, etc.)
            R.id.workoutHistoryFragment, R.id.workoutSessionDetailFragment, R.id.bodyMeasurementsFragment, R.id.measurementDetailFragment -> {
                when (destinationId) {
                    R.id.homeFragment -> {
                        // Same as profile -> home
                        navOptionsBuilder
                            .setEnterAnim(R.anim.fade_in)
                            .setExitAnim(R.anim.slide_out_down)
                            .setPopEnterAnim(R.anim.slide_in_up)
                            .setPopExitAnim(R.anim.fade_out)
                    }

                    R.id.exercisesFragment -> {
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left)
                    }

                    R.id.profileFragment -> {
                        // Back to profile from its sub-screens
                        navOptionsBuilder
                            .setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left)
                    }
                }
            }
            // Default horizontal slide animations for other cases
            else -> {
                navOptionsBuilder
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
            }
        }

        // Configure pop behavior for bottom navigation
        if (destinationId == R.id.homeFragment) {
            // When going to home, clear the back stack
            navOptionsBuilder.setPopUpTo(
                navController.graph.findStartDestination().id,
                inclusive = false,
                saveState = false
            )
        } else {
            // For other destinations, maintain the back stack
            navOptionsBuilder.setPopUpTo(
                navController.graph.findStartDestination().id,
                inclusive = false,
                saveState = destinationId != R.id.workoutDetailFragment
            )
        }

        val options = navOptionsBuilder.build()

        try {
            navController.navigate(destinationId, null, options)
        } catch (e: IllegalArgumentException) {
            Log.e("MainActivity", "Navigation error: ${e.message}")
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
                val activeSession = workoutRepo.getActiveWorkoutSession()
                if (activeSession != null) {
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
            } catch (e: Exception) {
                Log.e("MainActivity", "Error uploading pending workout sessions", e)
            }
        }
    }

    /**
     * Checks the offline login state from SharedPreferences and navigates accordingly.
     * If user was previously logged in, allows offline access to the app.
     */
    private fun checkOfflineLoginState() {
        if (preferencesManager.isLoggedIn()) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .setEnterAnim(R.anim.fade_in)
                .setExitAnim(R.anim.fade_out)
                .build()
            navController.navigate(R.id.homeFragment, null, navOptions)
            isAuthenticationResolved = true
            Log.d("MainActivity", "User allowed offline access based on stored login state")
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
                        // Save login state when authenticated online
                        val currentUser = authRepository.getCurrentUser()
                        preferencesManager.setLoggedIn(true)
                        currentUser?.email?.let { preferencesManager.setUserEmail(it) }
                        currentUser?.id?.let { preferencesManager.setUserId(it) }
                        // Save display_name if available - try different metadata fields
                        var displayName = currentUser?.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\"")
                        if (displayName.isNullOrEmpty()) {
                            displayName = currentUser?.userMetadata?.get("username")?.toString()?.removeSurrounding("\"")
                        }
                        if (displayName.isNullOrEmpty()) {
                            displayName = currentUser?.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        }
                        preferencesManager.setDisplayName(displayName)

                        downloadStarredExercisesInitial()
                        downloadWorkoutCardsInitial()
                        uploadPendingWorkoutSessions()

                        navController.currentDestination?.id.let { current ->
                            if (current == R.id.loginFragment || current == R.id.registerFragment) {
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .setEnterAnim(R.anim.slide_in_right)
                                    .setExitAnim(R.anim.slide_out_left)
                                    .build()
                                navController.navigate(R.id.homeFragment, null, navOptions)
                            }
                        }
                        isAuthenticationResolved = true
                    }
                    else -> {
                        if (!preferencesManager.isLoggedIn()) {
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true)
                                .setEnterAnim(R.anim.fade_in)
                                .setExitAnim(R.anim.fade_out)
                                .build()
                            navController.navigate(R.id.loginFragment, null, navOptions)
                        }
                        isAuthenticationResolved = true
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