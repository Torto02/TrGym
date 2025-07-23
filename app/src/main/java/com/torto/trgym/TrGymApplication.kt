package com.torto.trgym

import android.app.Application
import androidx.room.Room
import com.torto.trgym.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

// 1) La tua classe Application
@HiltAndroidApp
class TrGymApplication : Application() {

//    // lateinit per il singleton
//    lateinit var db: AppDatabase
//        private set
//
//    override fun onCreate() {
//        super.onCreate()
//
//        // 2) Inizializzi Room qui, al primo avvio del processo
//        db = Room.databaseBuilder(
//            applicationContext,
//            AppDatabase::class.java,
//            "TrGym.db"
//        )
//            // se hai un file SQLite pre‐popolato in assets/
//            .createFromAsset("TrGym.db")
//            .build()
//    }
}
