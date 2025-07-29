plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.torto.trgym"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.torto.trgym"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

// Configurazione KAPT per risolvere i conflitti di metadata
kapt {
    correctErrorTypes = true
    useBuildCache = true
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val roomVersion = "2.8.0-alpha01"
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // Supabase dependencies
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.0-beta-1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:3.1.3")

    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Hilt dependencies con versioni corrette
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Hilt per ViewModel (versioni aggiornate)
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")

    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

    implementation("com.github.bumptech.glide:glide:4.16.0") // o ultima versione
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("io.coil-kt:coil:2.5.0") // o l'ultima versione disponibile
    implementation("io.coil-kt:coil-gif:2.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0") // Or other formats if needed

}