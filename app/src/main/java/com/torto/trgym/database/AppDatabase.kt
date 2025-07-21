package com.torto.trgym.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.entities.*

@Database(
    entities = [Equipment::class, EquipmentI18n::class, Exercise::class, ExerciseEquipment::class,
        ExerciseI18n::class, ExerciseMedia::class, ExerciseMuscle::class, ExerciseTip::class,
        Language::class, Muscle::class, MuscleGroup::class, MuscleGroupI18n::class, MuscleI18n::class],
version = 1,
exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exercise.db"
                )
                    .createFromAsset("TrGym.db") // 👈 usa il file .db precompilato da assets/
                    .fallbackToDestructiveMigration() // opzionale
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
