package com.torto.trgym.di

import android.content.Context
import androidx.room.Room
import com.torto.trgym.database.AppDatabase
import com.torto.trgym.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {


    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext appContext: Context): AppDatabase {
        val builder = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "TrGym.db"
        )
            .createFromAsset("TrGym.db")
            .fallbackToDestructiveMigration() // Permette la migrazione distruttiva in caso di cambiamenti nel database
        return builder.build()
    }

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao {
        return db.exerciseDao()
    }

    @Provides
    fun provideLanguageDao(db: AppDatabase): LanguageDao {
        return db.languageDao()
    }

    @Provides
    fun provideMuscleDao(db: AppDatabase): MuscleDao {
        return db.muscleDao()
    }

    @Provides
    fun provideEquipmentDao(db: AppDatabase): EquipmentDao {
        return db.equipmentDao()
    }


    @Provides
    fun provideStarredExerciseDao(db: AppDatabase): StarredExerciseDao {
        return db.starredExerciseDao()
    }

    @Provides
    fun provideWorkoutDao(database: AppDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    fun provideBodyMeasurementDao(db: AppDatabase): BodyMeasurementDao {
        return db.bodyMeasurementDao()
    }
}