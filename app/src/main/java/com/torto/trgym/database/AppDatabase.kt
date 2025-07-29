package com.torto.trgym.database
import androidx.room.Database
import androidx.room.RoomDatabase
import com.torto.trgym.database.dao.*
import com.torto.trgym.database.entities.*

@Database(
    entities = [Equipment::class, EquipmentI18n::class, Exercise::class, ExerciseEquipment::class,
        ExerciseI18n::class, ExerciseMedia::class, ExerciseMuscle::class, ExerciseTip::class,
        Language::class, Muscle::class, MuscleGroup::class, MuscleGroupI18n::class,
        MuscleI18n::class, ExerciseStep::class, StarredExercise::class],
    version = 1, // Incrementata per la nuova tabella ,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun languageDao(): LanguageDao
    abstract fun muscleDao(): MuscleDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun starredExerciseDao(): StarredExerciseDao
}