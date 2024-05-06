import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.1.9"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

group = "at.asitplus"
version = "3.7.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    mavenLocal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.g0dkar:qrcode-kotlin:4.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation(napier())
    implementation("at.asitplus.wallet:vclib-openid:3.7.0-SNAPSHOT")
    implementation("at.asitplus.wallet:idacredential:3.4.0")
    implementation("at.asitplus.wallet:eupidcredential:1.0.0")
}


tasks.getByName<BootJar>("bootJar") {
    this.launchScript()
}

