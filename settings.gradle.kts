rootProject.name = "terminal_sp_server"

pluginManagement {
    repositories {
        maven {
            url = uri("https://raw.githubusercontent.com/a-sit-plus/gradle-conventions-plugin/mvn/repo")
            name = "aspConventions"
        }
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

include("service")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    versionCatalogs {
        create("vclib") {
            from("at.asitplus.wallet:vck-openid-versionCatalog:5.2.0-SNAPSHOT")
        }
    }
}

