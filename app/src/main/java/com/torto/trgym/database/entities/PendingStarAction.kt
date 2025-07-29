//package com.torto.trgym.database.entities
//
//import androidx.room.Entity
//import androidx.room.PrimaryKey
//
//@Entity(tableName = "pending_star_actions")
//data class PendingStarAction(
//    @PrimaryKey(autoGenerate = true) val id: Long = 0,
//    val exerciseId: Int,
//    val action: ActionType,           // enum { ADD, REMOVE }
//    val timestamp: Long = System.currentTimeMillis()
//)
//
//enum class ActionType { ADD, REMOVE }
