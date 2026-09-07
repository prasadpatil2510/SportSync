import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.firebase.appdistribution")
}

android {
    namespace = "club.nmtcc.cricket"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "club.nmtcc.cricket"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }

    buildFeatures { compose = true; buildConfig = true }

    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "API_BASE_URL", "\"https://development-api.example.workers.dev\"")
            buildConfigField("String", "API_WRITE_TOKEN", "\"\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            buildConfigField("String", "API_BASE_URL", "\"https://nmtcc-cricket-api-testing.nmtcccricketapi.workers.dev\"")
            buildConfigField("String", "API_WRITE_TOKEN", "\"\"")
            firebaseAppDistribution {
                artifactType = "APK"
                appId = providers.environmentVariable("FIREBASE_ANDROID_APP_ID").orNull
                    ?: "1:3540673696:android:a5090de7d45bdc40e055c5"
                testers = providers.environmentVariable("FIREBASE_TESTERS").orNull ?: ""
                releaseNotesFile = rootProject.file("firebase/release-notes.txt").absolutePath
                serviceCredentialsFile = providers.environmentVariable("FIREBASE_SERVICE_CREDENTIALS").orNull ?: ""
            }
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://production-api.example.workers.dev\"")
            buildConfigField("String", "API_WRITE_TOKEN", "\"\"")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
