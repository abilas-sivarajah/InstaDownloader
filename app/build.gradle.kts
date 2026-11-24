plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.clipfang"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.clipfang"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "EMBED_BASE", "\"https://www.instagram.com\"")
        buildConfigField("String", "EXTRA_MEDIA_HOST", "\"\"")
        manifestPlaceholders["cleartext"] = "false"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Mit dem lokalen Debug-Schlüssel signiert: installierbar, aber nicht für den Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
        // Testvariante: spricht statt mit Instagram mit tools/mock-server.js auf dem PC.
        create("mock") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mock"
            buildConfigField("String", "EMBED_BASE", "\"http://127.0.0.1:8099\"")
            buildConfigField("String", "EXTRA_MEDIA_HOST", "\"127.0.0.1\"")
            manifestPlaceholders["cleartext"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
