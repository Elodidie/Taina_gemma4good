plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.gemma"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gemma"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }

    aaptOptions {
        noCompress += "bin"
        noCompress += "task"
        noCompress += "litertlm"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    // Room (using ksp instead of kapt)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coil for photo thumbnails
    implementation("io.coil-kt:coil-compose:2.6.0")


    // Material
    implementation("com.google.android.material:material:1.12.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    // Extended icons (AddAPhoto, PhotoLibrary)
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

}