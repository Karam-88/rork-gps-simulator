import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing credentials come from an untracked key.properties in the Gradle root
// (android-gps-simulator/key.properties, git-ignored) with storeFile, storePassword,
// keyAlias and keyPassword. Nothing secret is ever committed. It is read through
// providers.fileContents so the configuration cache notices when the file changes.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("key.properties"))
        .asText.orNull?.let { load(it.reader()) }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

if (!hasReleaseSigning && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    logger.warn(
        "WARNING: key.properties is missing or incomplete - the release build is signed with the " +
            "DEBUG key and will be rejected by Google Play."
    )
}

// The Maps key is injected by GitHub Actions from GOOGLE_MAPS_API_KEY. Developers can
// also provide the same name as a Gradle property for local builds. The key itself is
// never committed; the production key is restricted in Google Cloud to this Android app.
val googleMapsApiKey = providers.environmentVariable("GOOGLE_MAPS_API_KEY")
    .orElse(providers.gradleProperty("GOOGLE_MAPS_API_KEY"))
    .orElse("")
    .get()

if (googleMapsApiKey.isBlank()) {
    logger.warn("WARNING: GOOGLE_MAPS_API_KEY is not configured; Google Maps will not render.")
}

android {
    namespace = "com.rork.gpssimulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rork.gpssimulator"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "1.1.14"
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Bundle native debug symbols when available from native dependencies.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            // Without key.properties (CI, fresh clones) fall back to the debug key so that
            // assembleRelease keeps working; such a build cannot be uploaded to Google Play.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)

    // Google is used only as the map renderer. Search/geocoding/routes continue to use
    // the app's existing non-Google services, so Places/Geocoding/Routes APIs are not needed.
    implementation("com.google.android.gms:play-services-maps:20.0.0")

    debugImplementation(libs.androidx.ui.tooling)
}
