package com.torto.trgym

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The `TrGymApplication` class serves as the entry point of the application.
 * It extends the `Application` class to provide a base context for the app.
 *
 * This class is annotated with `@HiltAndroidApp`, which triggers Hilt's code
 * generation and sets up dependency injection for the entire application.
 */
@HiltAndroidApp
class TrGymApplication : Application()