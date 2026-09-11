plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsh.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Unsigned release for CI; debug signing keeps the artifact installable.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The whale sprites are authored images whose alpha channel matters.
    // AGP's PNG cruncher re-encodes them (281 KB -> 375 KB on disk), which is
    // the kind of rewrite that previously left the overlay drawing nothing.
    // Keep the bytes exactly as authored.
    androidResources {
        cruncherEnabled = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
