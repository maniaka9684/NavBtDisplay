plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.fuzzyfly0.navbtdisplay"
    compileSdk = 37

    defaultConfig {
        // Own id, so this build installs next to the original NavBtDisplay.
        applicationId = "io.github.fuzzyfly0.navbtdisplay.bg"
        minSdk = 29
        targetSdk = 37
        versionCode = 9
        versionName = "0.2.0-bg.8"
    }

    // Fixed debug key in the repository: every GitHub build gets the same signature, so a new
    // version installs over the old one and keeps its settings and permissions.
    // (A debug key: fine for a personal app, not for publishing.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
}

dependencies {
    // Android Auto screen with the current hint (CarHintService).
    implementation("androidx.car.app:app:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
