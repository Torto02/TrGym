//package com.torto.trgym.database.dto
//
//import android.Manifest
//import android.content.Context
//import kotlinx.serialization.Serializable
//import android.net.ConnectivityManager
//import android.net.NetworkCapabilities
//import android.os.Build
//import androidx.annotation.RequiresPermission
//
//@Serializable
//data class StarredExerciseRemote(
//    val exercise_id: Int,
//    val user_id: String,
//    val created_at: String? = null
//)
//
//@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
//fun Context.isOnline(): Boolean {
//    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
//    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//        val net = cm.activeNetwork ?: return false
//        val caps = cm.getNetworkCapabilities(net) ?: return false
//        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//    } else {
//        @Suppress("DEPRECATION")
//        cm.activeNetworkInfo?.isConnected == true
//    }
//}
