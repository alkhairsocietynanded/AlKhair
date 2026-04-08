plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.aewsn.alkhair"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aewsn.alkhair"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
//    kapt(libs.hilt.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.markwon.core)

    implementation(libs.mpandroidchart)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
//    kapt(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)

    // ✅ CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)

    // ✅ ML Kit Barcode Scanning (BUNDLED version - No internet needed)
    implementation(libs.barcode.scanning)

    // Supabase
    implementation(platform(libs.bom))
    implementation(libs.postgrest.kt)
    implementation(libs.auth.kt)
    implementation(libs.storage.kt)
    implementation(libs.realtime.kt)
    implementation(libs.functions.kt)
    implementation(libs.ktor.client.okhttp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Glide (Image loading)
    implementation(libs.glide)
}
