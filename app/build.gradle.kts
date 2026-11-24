plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Die Versionsnummer kommt beim Release aus dem Git-Tag (v1.2.3 -> -PreleaseVersion=1.2.3).
// Daraus entsteht auch der versionCode (1.2.3 -> 10203), damit er nie vergessen wird.
val releaseVersion = providers.gradleProperty("releaseVersion").orNull
fun versionCodeOf(version: String): Int {
    val parts = Regex("""(\d{1,4})\.(\d{1,2})\.(\d{1,2})""").matchEntire(version)?.groupValues?.drop(1)?.map { it.toInt() }
        ?: throw GradleException("releaseVersion muss wie 1.2.3 aussehen (Minor und Patch bis 99), ist aber: $version")
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

// Release-Schlüssel: nur über Umgebungsvariablen, nie im Repo. Siehe tools/setup-signing.ps1.
val releaseKeystore = providers.environmentVariable("CLIPFANG_KEYSTORE").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "app.clipfang"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.clipfang"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
        versionName = releaseVersion ?: "dev"
        buildConfigField("String", "EMBED_BASE", "\"https://www.instagram.com\"")
        buildConfigField("String", "EXTRA_MEDIA_HOST", "\"\"")
        manifestPlaceholders["cleartext"] = "false"
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("CLIPFANG_KEYSTORE_PASSWORD").orNull
                keyAlias = "clipfang"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Ohne Release-Schlüssel (lokal, CI) wird mit dem Debug-Schlüssel signiert: nur zum Ausprobieren.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
