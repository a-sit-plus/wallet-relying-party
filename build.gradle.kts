plugins {
    id("at.asitplus.gradle.conventions") version "20250729"
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    kotlin("plugin.spring") version "2.2.0" apply false
    kotlin("plugin.jpa") version "2.2.0" apply false
    kotlin("plugin.allopen") version "2.2.0" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen")
        classpath("org.jetbrains.kotlin:kotlin-noarg")
    }
}

group = "at.asitplus"
