plugins {
    val kotlinVer = libs.versions.kotlin.get()
    id("at.asitplus.gradle.conventions") version "20260701"
    kotlin("jvm") version kotlinVer apply false
    kotlin("plugin.serialization") version kotlinVer apply false
    kotlin("plugin.spring") version kotlinVer apply false
    kotlin("plugin.jpa") version kotlinVer apply false
    kotlin("plugin.allopen") version kotlinVer apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen")
        classpath("org.jetbrains.kotlin:kotlin-noarg")
    }
}

group = "at.asitplus"
