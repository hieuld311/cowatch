import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.legacy.kapt)
}

android {
    namespace = "com.ivi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hieuld.cowatch"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "screen"
    productFlavors {
        create("cid") {
            dimension = "screen"
            applicationIdSuffix = ".cid"
            versionNameSuffix = "-cid"
        }
        create("pid") {
            dimension = "screen"
            applicationIdSuffix = ".pid"
            versionNameSuffix = "-pid"
        }
        create("rearLeft") {
            dimension = "screen"
            applicationIdSuffix = ".rear.left"
            versionNameSuffix = "-rear-left"
            buildConfigField("String", "SCREEN_ROLE", "\"REAR_LEFT\"")
        }
        create("rearRight") {
            dimension = "screen"
            applicationIdSuffix = ".rear.right"
            versionNameSuffix = "-rear-right"
            buildConfigField("String", "SCREEN_ROLE", "\"REAR_RIGHT\"")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    // Thumbnail decoding uses AssetManager.openFd(), which requires stored (uncompressed) assets.
    androidResources {
        noCompress += "mp4"
        noCompress += "m4v"
        noCompress += "webm"
        noCompress += "mkv"
        noCompress += "mov"
    }
    sourceSets {
        getByName("rearLeft") {
            kotlin.directories.add("src/rear/java")
            manifest.srcFile("src/rear/AndroidManifest.xml")
        }
        getByName("rearRight") {
            kotlin.directories.add("src/rear/java")
            manifest.srcFile("src/rear/AndroidManifest.xml")
        }
    }
}

hilt {
    enableAggregatingTask = true
}

val seekPreviewScript = rootProject.layout.projectDirectory.file("tools/generate_seek_previews.ps1")
val seekPreviewOutput = layout.projectDirectory.dir("src/main/assets/seekPreview")
val seekPreviewVideos = fileTree("src/main/assets/fileVideoSample") {
    include("**/*.mp4", "**/*.m4v", "**/*.webm", "**/*.mkv", "**/*.mov")
}

val generateSeekPreviews by tasks.registering(Exec::class) {
    group = "build"
    description = "Generates seek-preview frames shared by PID, CID, and rear apps."
    inputs.files(seekPreviewVideos).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(seekPreviewScript).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(seekPreviewOutput)
    commandLine(
        "powershell.exe",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        seekPreviewScript.asFile.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(generateSeekPreviews)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
