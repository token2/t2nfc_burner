plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.token2.burner3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.token2.nfcburner2"
        minSdk = 24
        targetSdk = 35
        versionCode = 31
        versionName = "3.0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // Store native libraries uncompressed and page-aligned. Combined with AGP 8.7+
    // this produces an APK/bundle compatible with 16 KB memory page devices.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // CameraX + ML Kit for QR scanning
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // Unbundled ML Kit barcode scanner: the native model is delivered via Google
    // Play Services rather than bundled in the APK, so there are no misaligned
    // native libraries (this keeps the app 16 KB-page compliant) and the APK is
    // smaller. The scanning API is identical to the bundled version.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    testImplementation("junit:junit:4.13.2")
}
