import at.asitplus.gradle.ktor
import at.asitplus.gradle.napier

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot") version libs.versions.spring.boot.get()
    id("at.asitplus.gradle.conventions")
}

val vckVersion: String by extra
val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion

kotlin {
    jvmToolchain(21)
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
    }
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.spring.cloud.get()}"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.session:spring-session-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.qrcode.kotlin)
    implementation(libs.spring.boot.admin.starter.client)
    implementation(ktor("http"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-logging"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation(napier())
    implementation(libs.wallet.vck.jvm)
    implementation(libs.wallet.vck.openid.jvm)
    implementation(libs.wallet.av)
    implementation(libs.wallet.eupid)
    implementation(libs.wallet.eupid.sdjwt)
    implementation(libs.wallet.mdl)
    implementation(libs.wallet.por)
    implementation(libs.wallet.cor)
    implementation(libs.wallet.taxid)
    implementation(libs.wallet.ehic)

    // Temporary dependency for Hpke decryption
    implementation("org.multipaz:multipaz:0.96.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
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


repositories {
    mavenCentral()
}
