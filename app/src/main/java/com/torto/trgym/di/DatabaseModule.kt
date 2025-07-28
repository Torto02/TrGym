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

/**
 * Modulo Dagger per la configurazione del database Room.
 * Fornisce le dipendenze necessarie per l'accesso al database e ai DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Fornisce un'istanza singleton di [AppDatabase].
     *
     * @param appContext Il contesto dell'applicazione, fornito da Hilt.
     * @return Un'istanza configurata di [AppDatabase].
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "TrGym.db"
        )
            // Crea il database a partire da un asset predefinito.
            .createFromAsset("TrGym.db")
            // Permette una migrazione distruttiva in caso di incompatibilità di schema.
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Fornisce un'istanza di [ExerciseDao] per l'accesso ai dati relativi agli esercizi.
     *
     * @param db L'istanza di [AppDatabase] da cui ottenere il DAO.
     * @return Un'istanza di [ExerciseDao].
     */
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
    fun provideEquipment(db: AppDatabase): EquipmentDao {
        return db.equipmentDao()
    }
}