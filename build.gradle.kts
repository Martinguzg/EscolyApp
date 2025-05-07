buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.2") // Actualizado para coincidir
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10") // Versión unificada
        classpath("com.google.gms:google-services:4.4.0")


    }
}

plugins {
    id("com.android.application") version "8.1.2" apply false // Misma versión que en buildscript
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}