import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Typed extension blocks instead of the `android {}` / `kotlinOptions {}`
// accessors: AGP 9 and Kotlin 2.4 reject those, and this form also works on
// AGP 8. Kotlin sources in src/main/kotlin are picked up by default.
extensions.configure<LibraryExtension> {
    namespace = "app.mylekha.client.flutter_usb_printer"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
