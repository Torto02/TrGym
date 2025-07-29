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
        val builder = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "TrGym.db"
        )
            .createFromAsset("TrGym.db")
//            .fallbackToDestructiveMigration() // Permette la migrazione distruttiva in caso di cambiamenti nel database
        return builder.build()
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
    fun provideEquipmentDao(db: AppDatabase): EquipmentDao {
        return db.equipmentDao()
    }

    /**
     * Fornisce un'istanza di [StarredExerciseDao] per l'accesso ai dati relativi agli esercizi preferiti.
     *
     * @param db L'istanza di [AppDatabase] da cui ottenere il DAO.
     * @return Un'istanza di [StarredExerciseDao].
     */
    @Provides
    fun provideStarredExerciseDao(db: AppDatabase): StarredExerciseDao {
        return db.starredExerciseDao()
    }
}