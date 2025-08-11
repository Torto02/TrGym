package com.torto.trgym.database.dao

import androidx.room.*
import com.torto.trgym.database.entities.StarredExercise
import kotlinx.coroutines.flow.Flow

/**
 * DAO per la gestione degli esercizi preferiti.
 * Fornisce metodi per aggiungere, rimuovere e verificare gli esercizi preferiti.
 */
@Dao
interface StarredExerciseDao {

    /**
     * Verifica se un esercizio è tra i preferiti.
     * @param exerciseId L'ID dell'esercizio da verificare
     * @return Flow<Boolean> che emette true se l'esercizio è preferito, false altrimenti
     */
    @Query("SELECT EXISTS(SELECT 1 FROM starred_exercises WHERE exercise_id = :exerciseId)")
    fun isExerciseStarred(exerciseId: Int): Flow<Boolean>

    /**
     * Aggiunge un esercizio ai preferiti.
     * @param starredExercise L'esercizio da aggiungere ai preferiti
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToStarred(starredExercise: StarredExercise)

    @Query("DELETE FROM starred_exercises WHERE exercise_id = :exerciseId")
    suspend fun removeFromStarred(exerciseId: Int)

    /**
     * Ottiene tutti gli esercizi preferiti.
     * @return Flow<List<Int>> Lista degli ID degli esercizi preferiti
     */
    @Query("SELECT exercise_id FROM starred_exercises")
    fun getAllStarredExerciseIds(): Flow<List<Int>>

    /**
     * Rimuove tutti gli esercizi dai preferiti.
     */
    @Query("DELETE FROM starred_exercises")
    suspend fun clearAllStarred()

    /**
     * Inserisce una lista di esercizi tra quelli preferiti.
     * @param exercises Lista di esercizi da inserire tra i preferiti
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStarredExercises(exercises: List<StarredExercise>)

    /**
     * Marca un esercizio come sincronizzato con il server.
     * @param exerciseId L'ID dell'esercizio da marcare come sincronizzato
     */
    @Query("UPDATE starred_exercises SET synced_with_server = 1 WHERE exercise_id = :exerciseId")
    suspend fun markAsSynced(exerciseId: Int)

    /**
     * Ottiene tutti gli esercizi preferiti che non sono ancora sincronizzati con il server.
     * @return Lista degli esercizi che necessitano sincronizzazione
     */
    @Query("SELECT * FROM starred_exercises WHERE synced_with_server = 0")
    suspend fun getUnsyncedStarredExercises(): List<StarredExercise>

    /**
     * Ottiene tutti gli esercizi preferiti locali.
     * @return Lista di tutti gli esercizi preferiti
     */
    @Query("SELECT * FROM starred_exercises")
    suspend fun getAllLocalStarredExercises(): List<StarredExercise>
}