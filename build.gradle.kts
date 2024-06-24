plugins {
    id("at.asitplus.gradle.conventions") version "2.0.0+20240610"
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen")
        classpath("org.jetbrains.kotlin:kotlin-noarg")
    }
}

group = "at.asitplus"
