import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.oz.android.ads"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        buildConfig = true
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

    //noinspection UseTomlInstead
    dependencies {
        // AndroidX
        implementation("androidx.appcompat:appcompat:1.7.1")
        implementation("androidx.core:core-ktx:1.17.0")
        implementation("androidx.constraintlayout:constraintlayout:2.2.1")
        implementation("androidx.window:window:1.5.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
        implementation("androidx.lifecycle:lifecycle-process:2.10.0")


        // Kotlin Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

        // Google Play Services Ads + UMP
        implementation("com.google.android.gms:play-services-ads:24.9.0")
        implementation("com.google.android.ump:user-messaging-platform:4.0.0")

        // Shimmer
        implementation("io.github.usefulness:shimmer-android-core:1.0.0")

        // Testing
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.3.0")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

        // Firebase (using BOM for version alignment)
        implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
        implementation("com.google.firebase:firebase-analytics")
    }
}

