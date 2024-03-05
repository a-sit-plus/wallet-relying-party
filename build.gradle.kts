plugins {
    id("at.asitplus.gradle.conventions") version "1.9.20+20231114"
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen")
        classpath("org.jetbrains.kotlin:kotlin-noarg")
    }
}

group = "at.asitplus"
