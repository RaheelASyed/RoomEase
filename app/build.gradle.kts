plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "edu.virginia.cs4720.roomease"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "edu.virginia.cs4720.roomease"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Point debug builds at the local Firebase Emulator Suite with: -Proomease.useEmulator=true
        val useEmulator = (project.findProperty("roomease.useEmulator") as String?) == "true"
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useEmulator.toString())
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Firebase (Lecture 11: Auth, Lecture 12: Firestore). The BoM pins compatible versions.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // .await() on Firebase Tasks inside coroutines (Lecture 11)
    implementation(libs.kotlinx.coroutines.play.services)

    // MVVM + navigation (Lectures 6, 7, 9)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Local settings (Lecture 9: Preferences DataStore)
    implementation(libs.androidx.datastore.preferences)
    // Icons for IconButton (Lecture 8)
    implementation(libs.androidx.compose.material.icons.extended)
    // Receipt scanning: CameraX preview/capture + on-device ML Kit text recognition
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.text.recognition)
    // Google sign-in through Credential Manager + Firebase Auth
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}