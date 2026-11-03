import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.garam.hackathon"
    // Newer AndroidX libraries need compileSdk 35+. targetSdk stays at the emulator's API 34.
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    // CarUxRestrictionsManager for the driving state (docs/sdk-notes.md 3.14).
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.garam.hackathon"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.0"
    }

    flavorDimensions += "mode"
    productFlavors {
        // The build submitted to Pleos.
        create("prod") { dimension = "mode" }
        // Contest demo: adds the synthetic-month generator (src/demo). Never submitted.
        create("demo") {
            dimension = "mode"
            versionNameSuffix = "-demo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local testing only: -PdebugSignedRelease signs with the debug key so the build installs.
            // Distribution needs the production certificate (app security guide).
            if (project.hasProperty("debugSignedRelease")) signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // android.util.Log calls return defaults in JVM unit tests.
    testOptions.unitTests.isReturnDefaultValues = true
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
