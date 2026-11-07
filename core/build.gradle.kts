import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    // Desktop PDFBox (what pdfbox-android is ported from), to test reading a real password-protected PDF.
    testImplementation("org.apache.pdfbox:pdfbox:2.0.32")
}

tasks.test {
    useJUnit()
}

// ./gradlew :core:checkSnapshot -Psnapshot=pipeline/out/snapshot.json.gz -PminStocks=500
tasks.register<JavaExec>("checkSnapshot") {
    group = "verification"
    description = "Loads a snapshot like the app does and runs every preset screen."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.akrishna87.sievr.core.tools.CheckSnapshotKt")
    args(
        (project.findProperty("snapshot") as String?) ?: "../pipeline/out/snapshot.json.gz",
        (project.findProperty("minStocks") as String?) ?: "100",
    )
}
