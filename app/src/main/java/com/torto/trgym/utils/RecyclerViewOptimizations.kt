package com.torto.trgym.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.torto.trgym.R
import kotlin.collections.forEach
import kotlin.collections.take

object RecyclerViewOptimizations {

    // RequestOptions condivise per Glide
    val glideOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(200, 200)
        .dontAnimate() // Rimuove animazioni per caricamento più veloce
        .fitCenter() // Mantieni l'aspect ratio
        .placeholder(R.drawable.ic_fitness_center)
        .error(R.drawable.ic_fitness_center)


    // Precarica la cache di Glide con le prime immagini
    fun preloadInitialImages(context: Context, imageNames: List<String>, count: Int = 10) {
        try {
            val glide = Glide.with(context.applicationContext)
            imageNames.take(count).forEach { imageName ->
                val assetPath = "file:///android_asset/images/$imageName-1.jpeg"
                glide.load(assetPath)
                    .apply(glideOptions)
                    .preload()
            }
        } catch (e: Exception) {
            // Ignora errori di precaricamento
        }
    }
}