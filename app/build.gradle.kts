import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
//    id("com.google.gms.google-services")
    id("kotlin-parcelize")
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    alias(libs.plugins.google.devtool.ksp)
    alias(libs.plugins.google.dagger.hilt.android)
}

android {
    namespace = "id.monpres.app"
    compileSdk = 36

    // Read GOOGLE_SERVER_CLIENT_ID from local.properties
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    defaultConfig {
        applicationId = "id.monpres.app"
        minSdk = 24
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

            // --- USE THE PRE-LOADED PROPERTIES ---
            val releaseClientId = localProperties.getProperty("GOOGLE_SERVER_CLIENT_ID_RELEASE")
                ?: throw GradleException("GOOGLE_SERVER_CLIENT_ID_RELEASE not found in local.properties")

            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$releaseClientId\"")
            resValue("string", "google_server_client_id", releaseClientId)
        }

        debug {
            // --- USE THE PRE-LOADED PROPERTIES ---
            val debugClientId = localProperties.getProperty("GOOGLE_SERVER_CLIENT_ID")
                ?: throw GradleException("GOOGLE_SERVER_CLIENT_ID not found in local.properties")

            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$debugClientId\"")
            resValue("string", "google_server_client_id", debugClientId)

            // It's also good practice to add a custom application ID suffix for debug builds
            // to allow installing both debug and release versions on the same device.
            // applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            pickFirsts.addAll(listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            ))
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.annotation)

    // Transition
    implementation(libs.androidx.transition)
    implementation(libs.androidx.transition.ktx)

    // Room
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.room.paging)
    implementation(libs.gson)
    implementation(libs.androidx.paging.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    /* Firebase */
    implementation(platform(libs.firebase.bom))

    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.messaging.directboot)

    // Credential Manager libraries
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    /* Mapbox */
    implementation(libs.mapbox)

    // Turf module for spatial processing like distance calculation
    implementation(libs.mapbox.sdk.turf)

    /* Glide */
    implementation(libs.glide)

    /* Slide to act */
    implementation(libs.slidetoact)

    /* Lottie */
    implementation(libs.dotlottie.android)
    implementation(libs.lottie)

    /* libphonenumber for phone number parsing, formatting, and validation */
    implementation(libs.libphonenumber)

    implementation(libs.play.services.location)

    // Preference
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Skeleton layout
    implementation(libs.skeletonlayout)

    // ViewBinding delegation
    implementation(libs.vbpd)

    // Memory leak check
    debugImplementation(libs.leakcanary.android)
}
