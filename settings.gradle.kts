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
    }
    versionCatalogs {
        create("vclib") {
            from("at.asitplus.wallet:vclib-openid-versionCatalog:3.7.0")
        }
    }
}

