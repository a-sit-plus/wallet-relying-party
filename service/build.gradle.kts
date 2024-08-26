import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.1.12"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

group = "at.asitplus"
version = "4.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

repositories {
    maven(url = uri("https://s01.oss.sonatype.org/content/repositories/atasitplus-1173/"))
    maven(url = uri("https://s01.oss.sonatype.org/content/repositories/atasitplus-1175/"))
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
    implementation("at.asitplus.wallet:vck-openid:4.1.1")
    implementation("at.asitplus.wallet:idacredential:3.8.3")
    implementation("at.asitplus.wallet:eupidcredential:2.1.3")
    implementation("at.asitplus.wallet:mobiledrivinglicence:1.0.2")
    implementation("at.asitplus.wallet:powerofrepresentation:1.0.2")
    implementation("at.asitplus.wallet:certificateofresidence:1.0.2")
    implementation("at.asitplus.wallet:eprescription:1.0.1")
}


tasks.getByName<BootJar>("bootJar") {
    this.launchScript()
}

