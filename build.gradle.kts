// Plugins go on the build classpath here so the Kotlin plugin can see the Android plugin.
// The Android plugin is only added where the Android SDK is installed (see settings.gradle.kts),
// so the plain-Kotlin core module also builds and tests on machines without it.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    val kotlinVersion = "2.2.21"
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").exists()
    dependencies {
        if (hasAndroidSdk) classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    }
}
