plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Pass your deployed server with: gradle assembleDebug -PserverUrl=wss://your-server.example.com
// Default (10.0.2.2) reaches a server running on your computer from the Android emulator.
val serverUrl: String = (project.findProperty("serverUrl") as String?)
    ?.takeIf { it.isNotBlank() } ?: "ws://10.0.2.2:8080"

android {
    namespace = "com.example.randomchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.randomchat"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        manifestPlaceholders["cleartext"] = if (serverUrl.startsWith("ws://")) "true" else "false"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
