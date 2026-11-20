import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace  = "com.openlauncher.app"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId  = "com.pumalauncher.app"
        minSdk         = 29
        targetSdk      = 33
        versionCode    = 3
        versionName    = "0.1.2"
    }

    base {
        archivesName.set("PumaLauncher-v${defaultConfig.versionName}")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile     = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias      = keystoreProps.getProperty("keyAlias")
                keyPassword   = keystoreProps.getProperty("keyPassword")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Media Station: Media3 (ExoPlayer + HLS + UI + FFmpeg audio decoder) and OkHttp (JSON via Gson above)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.13.1")

    // MapLibre Native SDK & Location Services
    implementation("org.maplibre.gl:android-sdk:11.+")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
