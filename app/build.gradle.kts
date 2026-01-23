plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    kotlin("kapt") // kapt zaroori hai
}

android {
    namespace = "com.zabibtech.alkhair"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zabibtech.alkhair"
        minSdk = 26
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)



    // MVVM + Coroutines
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // for Task.await()
    implementation(libs.kotlinx.serialization.json)
// UI
    implementation(libs.androidx.recyclerview)
    implementation(libs.view)

// Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ViewModel integration (agar chahiye)
//    implementation (libs.androidx.hilt.lifecycle.viewmodel)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)

    implementation(libs.mpandroidchart)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // ✅ CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ✅ ML Kit Barcode Scanning (BUNDLED version - No internet needed)
    implementation(libs.barcode.scanning)

    // Supabase
    implementation(platform(libs.bom))
    implementation(libs.postgrest.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.auth.kt)
    implementation(libs.storage.kt)
}