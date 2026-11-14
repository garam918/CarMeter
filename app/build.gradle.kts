import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The release key lives outside the repository (scripts/create-release-key.sh makes it).
// -Pcarmeter.signing=<file> points at another properties file. Without one, release
// builds come out unsigned and :app:submissionApk refuses to copy them.
val releaseSigning: Properties? = run {
    val path = providers.gradleProperty("carmeter.signing").orNull
        ?: "${System.getProperty("user.home")}/.carmeter-signing/keystore.properties"
    File(path).takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
}
val debugSignedRelease = project.hasProperty("debugSignedRelease")

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
        versionCode = 5
        versionName = "1.0.0"
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // APK Signature Scheme v2 only (AGP's choice at minSdk 28). With v3 on, apksig
                // drops v2 at this minSdk; v3 can come back with a key rotation lineage.
                enableV2Signing = true
            }
        }
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
            // Distribution needs a release certificate, never the debug one (app security guide).
            // -PdebugSignedRelease is for local measurement on a machine without the release key.
            signingConfig = when {
                debugSignedRelease -> signingConfigs.getByName("debug")
                releaseSigning != null -> signingConfigs.getByName("release")
                else -> null
            }
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

// Contest submission: apk/<team>_<app>_release.apk (names in gradle.properties).
tasks.register("submissionApk") {
    group = "distribution"
    description = "Builds the signed prodRelease APK and copies it to apk/ under the contest file name."
    dependsOn("assembleProdRelease")
    val apk = layout.buildDirectory.file("outputs/apk/prod/release/app-prod-release.apk")
    val outDir = rootProject.layout.projectDirectory.dir("apk")
    val team = providers.gradleProperty("submission.team").map { it.trim() }.orElse("")
    val app = providers.gradleProperty("submission.app").map { it.trim() }.orElse("")
    val signed = releaseSigning != null && !debugSignedRelease
    doLast {
        if (!signed) throw GradleException("릴리스 키가 없거나 -PdebugSignedRelease로 빌드했습니다. scripts/create-release-key.sh로 키를 만든 뒤 다시 실행하세요.")
        if (team.get().isEmpty() || app.get().isEmpty()) throw GradleException("gradle.properties의 submission.team과 submission.app을 채우세요.")
        val source = apk.get().asFile
        if (!source.isFile) throw GradleException("서명된 APK가 없습니다: $source")
        val target = outDir.file("${team.get()}_${app.get()}_release.apk").asFile
        target.parentFile.mkdirs()
        source.copyTo(target, overwrite = true)
        println("제출용 APK: ${target.relativeTo(rootDir)}")
    }
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
