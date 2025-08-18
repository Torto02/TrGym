plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.torto.trgym.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.torto.trgym.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.constraintlayout)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

        implementation ("androidx.core:core-ktx:1.9.0")
        implementation ("com.google.android.gms:play-services-wearable:18.1.0")
        implementation ("androidx.wear:wear:1.3.0")
        implementation ("androidx.wear:wear-ongoing:1.0.0")
        implementation ("androidx.wear.tiles:tiles:1.1.0")
        implementation ("androidx.wear.tiles:tiles-material:1.1.0")
        implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
        implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
}