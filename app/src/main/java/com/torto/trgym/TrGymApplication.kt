//package com.torto.trgym
//
//import android.app.Application
//import dagger.hilt.android.HiltAndroidApp
//
//
//@HiltAndroidApp
//class TrGymApplication : Application() {
//
//}


package com.torto.trgym

import android.app.Application
import android.content.Context
import com.torto.trgym.utils.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrGymApplication : Application() {

    companion object {
        lateinit var currentLanguage: String
            private set
    }

    override fun attachBaseContext(base: Context) {
        val lang = LocaleManager.getStoredLanguage(base)
        currentLanguage = lang
        val updatedContext = LocaleManager.updateResources(base, lang)
        super.attachBaseContext(updatedContext)
    }
}
