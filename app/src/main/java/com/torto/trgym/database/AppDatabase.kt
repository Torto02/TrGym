package com.torto.trgym.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.entities.*

/**
 * AppDatabase è la classe principale per la gestione del database Room.
 * Definisce le entità e la versione del database, e fornisce l'accesso ai DAO.
 *
 * @property entities Le entità che rappresentano le tabelle del database.
 * @property version La versione del database.
 * @property exportSchema Indica se lo schema del database deve essere esportato.
 */
@Database(
    entities = [Equipment::class, EquipmentI18n::class, Exercise::class, ExerciseEquipment::class,
        ExerciseI18n::class, ExerciseMedia::class, ExerciseMuscle::class, ExerciseTip::class,
        Language::class, Muscle::class, MuscleGroup::class, MuscleGroupI18n::class, MuscleI18n::class, ExerciseStep::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Fornisce l'accesso al DAO per la gestione delle operazioni sui dati relativi agli esercizi.
     *
     * @return Un'istanza di [ExerciseDao].
     */
    abstract fun exerciseDao(): ExerciseDao
    abstract fun languageDao(): LanguageDao
}