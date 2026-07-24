import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.franciscor.agendnote"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.franciscor.agendnote"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            val releaseStoreFile = System.getenv("ANDROID_RELEASE_STORE_FILE")
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(compose.components.uiToolingPreview)
    debugImplementation(compose.uiTooling)
}
