package com.torto.trgym.database.dto

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.torto.trgym.database.entities.*

data class ExerciseDetail(
    @Embedded val exercise: Exercise,

    // localized title and primer
    @Relation(
        parentColumn = "id",
        entityColumn = "exercise_id",
        entity = ExerciseI18n::class
    )
    val translations: List<ExerciseI18n>,

    // media for the exercise
    @Relation(
        parentColumn = "id",
        entityColumn = "exercise_id",
        entity = ExerciseMedia::class
    )
    val media: List<ExerciseMedia>,

    // tips for the exercise
    @Relation(
        parentColumn = "id",
        entityColumn = "exercise_id",
        entity = ExerciseTip::class
    )
    val tips: List<ExerciseTip>,

    // step-by-step instructions
    @Relation(
        parentColumn = "id",
        entityColumn = "exercise_id",
        entity = ExerciseStep::class
    )
    val steps: List<ExerciseStep>,

    // muscles involved, with localized names
    @Relation(
        parentColumn = "id",
        entity = MuscleI18n::class,
        entityColumn = "muscle_id",
        associateBy = Junction(
            value = ExerciseMuscle::class,
            parentColumn = "exercise_id",
            entityColumn = "muscle_id"
        )
    )
    val muscles: List<MuscleI18n>,

    // equipment required, with localized names
    @Relation(
        parentColumn = "id",
        entity = EquipmentI18n::class,
        entityColumn = "equipment_id",
        associateBy = Junction(
            value = ExerciseEquipment::class,
            parentColumn = "exercise_id",
            entityColumn = "equipment_id"
        )
    )
    val equipment: List<EquipmentI18n>
)
