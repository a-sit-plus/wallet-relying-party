import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.URI

plugins {
	id("org.springframework.boot") version "3.1.0"
	id("io.spring.dependency-management") version "1.1.0"
	kotlin("jvm") version "1.8.21"
	kotlin("plugin.spring") version "1.8.21"
	kotlin("plugin.serialization") version "1.8.21"
}

group = "at.asit.apps.terminal_sp.prototype.server"
version = "2.2.1"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

val gitLabPrivateToken: String? by extra
val gitLabGroupId: String by extra

repositories {
	mavenCentral()
	maven {
		this.url = URI.create("https://s01.oss.sonatype.org/content/repositories/snapshots/")
	}
	mavenLocal()

	if (System.getenv("CI_JOB_TOKEN") != null || gitLabPrivateToken != null) {
		maven {
			name = "gitlab"
			url = uri("https://gitlab.iaik.tugraz.at/api/v4/groups/$gitLabGroupId/-/packages/maven")
			if (gitLabPrivateToken != null) {
				credentials(HttpHeaderCredentials::class) {
					name = "Private-Token"
					value = gitLabPrivateToken
				}
			} else if (System.getenv("CI_JOB_TOKEN") != null) {
				credentials(HttpHeaderCredentials::class) {
					name = "Job-Token"
					value = System.getenv("CI_JOB_TOKEN")
				}
			}
			authentication {
				create<HttpHeaderAuthentication>("header")
			}
		}
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-mustache")
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
//	runtimeOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")

//	implementation("org.springframework.boot:spring-boot-starter-data-redis")
//	implementation("org.springframework.session:spring-session-data-redis")
//
//	implementation("io.lettuce:lettuce-core:{lettuce-core-version}")

	implementation("io.github.aakira:napier:2.6.1")
	// https://mvnrepository.com/artifact/at.asitplus.wallet/vclib
	implementation("at.asitplus.wallet:vclib-openid:2.1.0-SNAPSHOT")
	//  implementation("at.asitplus.wallet:idacredential:2.0.2-SNAPSHOT")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.getByName<BootJar>("bootJar") {
	this.launchScript()
}