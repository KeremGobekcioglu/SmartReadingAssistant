
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.secrets)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"

}




android {
    namespace = "com.gobex.smartreadingassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gobex.smartreadingassistant"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"


            //} It is highly likely you will get errors for these next,
            // so I recommend excluding them now to save you time:
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}
secrets {
    // To add your Maps API key to this project:
    // 1. Open the "local.properties" file
    // 2. Add this line: GEMINI_API_KEY=YOUR_API_KEY
    // 3. The plugin will automatically create a variable "BuildConfig.GEMINI_API_KEY"
    propertiesFileName = "local.properties"
//    defaultPropertiesFileName = "local.defaults.properties"
}
dependencies {
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.genai)
    // ML Kit Text Recognition
    implementation(libs.mlkit.text.recognition)

    // Coroutines Play Services (for ML Kit await())
    implementation(libs.kotlinx.coroutines.play.services.v1102)
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")


    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Core Nordic BLE Library
    implementation("no.nordicsemi.android:ble:2.7.1")
// KTX version for Coroutines (this is what makes it clean)
    implementation("no.nordicsemi.android:ble-ktx:2.7.1")
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.navigation:navigation-compose:2.8.0-alpha08") // or newer
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    // Coil for image loading
    implementation(libs.coil.compose)
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) // For Kotlin extensions and Coroutines support
    ksp(libs.androidx.room.compiler) // KSP for annotation processing
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.mlkit:language-id:17.0.5")
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing) // For Room testing
    implementation(libs.logging.interceptor.v4110)
}