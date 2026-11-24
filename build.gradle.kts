plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

// Optional: -PbuildRoot=<kurzer Pfad>, falls der Projektpfad unter Windows zu lang ist.
providers.gradleProperty("buildRoot").orNull?.let { root ->
    allprojects { layout.buildDirectory.set(file("$root/${project.name}")) }
}
