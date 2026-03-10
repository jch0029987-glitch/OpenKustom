plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openkustom.lwp"
    compileSdk = 36 // Android 16

    defaultConfig {
        applicationId = "com.openkustom.lwp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.json:json:20240303") 
}
