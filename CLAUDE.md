# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TrGym is an Android fitness application built with Kotlin that provides exercise management, workout tracking, and user authentication. The app uses a modern Android architecture with dependency injection, local database caching, and cloud synchronization.

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Install debug APK to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Run ktlint (if configured)
./gradlew ktlintCheck
```

## Architecture Overview

### Technology Stack
- **Language**: Kotlin
- **Architecture Pattern**: MVVM with Repository pattern
- **Dependency Injection**: Dagger Hilt
- **Database**: Room (local) with SQLite
- **Backend**: Supabase (auth, realtime sync)
- **Navigation**: Android Navigation Component
- **UI**: View Binding with Fragments
- **Async**: Coroutines and Flow
- **Image Loading**: Glide and Coil

### Key Components

#### Database Layer (`database/`)
- **AppDatabase.kt**: Room database configuration with entities for exercises, workouts, users
- **Entities**: Exercise, WorkoutCard, WorkoutExercise, ExerciseSet, Equipment, Muscle, StarredExercise
- **DAOs**: Type-safe database access objects for each entity type
- **DTOs**: Data transfer objects for complex queries and API responses

#### Repository Layer (`data/repository/`)
- **ExerciseRepository**: Manages exercise data, filtering, starred exercises, Supabase sync
- **WorkoutRepository**: Handles workout cards, exercises, sets
- **AuthRepository**: User authentication, session management with Supabase

#### UI Layer (`ui/`)
- **Fragments**: LoginFragment, HomeFragment, ExercisesFragment, WorkoutDetailFragment, ProfileFragment
- **ViewModels**: Reactive UI state management using LiveData/StateFlow
- **Adapters**: RecyclerView adapters for exercises, workouts, exercise sets

#### Dependency Injection (`di/`)
- **DatabaseModule**: Provides Room database and DAO instances
- **SupabaseModule**: Configures Supabase client for auth and data sync

### Database Schema
The app uses a pre-populated SQLite database (`TrGym.db` in assets/) containing:
- Exercise library with internationalization (multiple languages)
- Equipment and muscle group data
- Media assets (images, GIFs, videos) for exercise demonstrations
- User-specific data (starred exercises, workout cards) synced with Supabase

### Authentication Flow
1. App starts with splash screen during auth resolution
2. Supabase session status determines initial navigation (LoginFragment or HomeFragment)
3. Deep link handling for OAuth flows
4. Bottom navigation visibility controlled by auth state
5. Starred exercises sync after authentication

### Navigation Structure
- **Start Destination**: LoginFragment (if not authenticated)
- **Main Tabs**: Home, Exercises, Profile
- **Key Flows**: 
  - Auth: Login ↔ Register → Home
  - Exercise Discovery: Exercises → ExerciseDetail
  - Workout Management: Home → WorkoutDetail

## Development Guidelines

### File Organization
- Follow package-by-feature structure under `com.torto.trgym`
- Keep UI components in `ui/` with corresponding ViewModels in `viewmodel/`
- Database-related code in `database/` (entities, DAOs, DTOs)
- Business logic in `data/repository/`

### Common Patterns
- Use Hilt `@AndroidEntryPoint` for Activities/Fragments requiring injection
- Implement ViewModels with `@HiltViewModel` for automatic injection
- Use StateFlow/LiveData for reactive UI updates
- Leverage Room's Flow-based queries for automatic UI updates
- Handle auth state changes reactively in MainActivity

### Testing
- Unit tests in `src/test/`
- Instrumented tests in `src/androidTest/`
- Use TestInstrumentationRunner for Android-specific tests

### Key Dependencies
- Room: 2.8.0-alpha01 (database)
- Supabase: 3.2.0-beta-1 (backend)
- Hilt: 2.53.1 (DI)
- Navigation: 2.7.x (navigation)
- Glide/Coil: Image loading
- WorkManager: Background tasks

### Multi-language Support
The app includes internationalization support through:
- LocaleManager utility for runtime language switching
- Database entities with I18n tables (ExerciseI18n, EquipmentI18n, etc.)
- Stored language preferences