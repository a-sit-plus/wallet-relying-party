import at.asitplus.gradle.ktor
import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.2.10"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
}

group = "at.asitplus"
version = "5.2.1-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.session:spring-session-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.g0dkar:qrcode-kotlin:4.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("de.codecentric:spring-boot-admin-client:3.2.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation(napier())
    implementation("at.asitplus.wallet:vck-openid:5.2.1-SNAPSHOT")
    implementation("at.asitplus.wallet:idacredential:3.9.1")
    implementation("at.asitplus.wallet:eupidcredential:2.2.1")
    implementation("at.asitplus.wallet:mobiledrivinglicence:1.1.1")
    implementation("at.asitplus.wallet:powerofrepresentation:1.1.0")
    implementation("at.asitplus.wallet:certificateofresidence:2.1.0")
    implementation("at.asitplus.wallet:eprescription:1.1.0")


    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation(ktor("client-java"))
}

tasks.test {
    testLogging {
        showExceptions = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
    useJUnitPlatform()
}

springBoot {
    buildInfo()
}

tasks.getByName<BootJar>("bootJar") {
    this.launchScript()
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}
