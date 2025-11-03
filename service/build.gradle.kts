import at.asitplus.gradle.ktor
import at.asitplus.gradle.napier
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot") version "3.5.7"
    id("at.asitplus.gradle.conventions")
}

val vckVersion: String by extra
val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion

kotlin {
    jvmToolchain(17)
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
    }
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.session:spring-session-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.g0dkar:qrcode-kotlin:4.5.0")
    implementation("de.codecentric:spring-boot-admin-client:3.5.1")
    implementation(ktor("http"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-logging"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation(napier())
    implementation("at.asitplus.wallet:vck:$vckVersion")
    implementation("at.asitplus.wallet:vck-openid:$vckVersion")
    implementation("at.asitplus.wallet:eupidcredential:3.2.0")
    implementation("at.asitplus.wallet:eupidcredential-sdjwt:1.2.0")
    implementation("at.asitplus.wallet:mobiledrivinglicence:1.2.0")
    implementation("at.asitplus.wallet:powerofrepresentation:1.3.0")
    implementation("at.asitplus.wallet:certificateofresidence:2.2.0")
    implementation("at.asitplus.wallet:healthid:2.2.0")
    implementation("at.asitplus.wallet:company-registration:1.2.0")
    implementation("at.asitplus.wallet:taxid:1.2.0")
    implementation("at.asitplus.wallet:ehic:1.2.0")

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
}
