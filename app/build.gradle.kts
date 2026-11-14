plugins {
    id("com.android.application")
}

android {
    namespace = "fr.halalscan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.halalscan.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
