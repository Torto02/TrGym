//package com.torto.trgym.database.dao
//
//import androidx.room.Dao
//import androidx.room.Delete
//import androidx.room.Insert
//import androidx.room.Query
//import com.torto.trgym.database.entities.PendingStarAction
//
//@Dao
//interface PendingStarActionDao {
//    @Insert
//    suspend fun insert(action: PendingStarAction)
//    @Query("SELECT * FROM pending_star_actions ORDER BY timestamp")
//    fun getAll(): List<PendingStarAction>
//    @Delete
//    suspend fun delete(action: PendingStarAction)
//}
