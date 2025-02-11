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
            from("at.asitplus.wallet:vck-openid-versionCatalog:5.4.0")
        }
    }
}



// //If we have a working composite build, use it!
// if (File("../kmm-vc-library/signum").isDirectory && File("../kmm-vc-library/signum/build.gradle.kts").exists()) {
//     logger.warn("Detected VC-K in ${File("../kmm-vc-library").absolutePath}.")
//     logger.warn("Including VC-K and Signum as composite build.")
//     logger.warn("If you do not want this, move the VC-K to another location!")
//     includeBuild("../kmm-vc-library/signum") {
//         dependencySubstitution {
//             substitute(module("at.asitplus.wallet:indispensable")).using(project(":indispensable"))
//             substitute(module("at.asitplus.signum:indispensable-josef")).using(project(":indispensable-josef"))
//             substitute(module("at.asitplus.signum:indispensable-cosef")).using(project(":indispensable-cosef"))
//             substitute(module("at.asitplus.signum:supreme")).using(project(":supreme"))
//         }
//     }
//     includeBuild("../kmm-vc-library") {
//         dependencySubstitution {
//             substitute(module("at.asitplus.wallet:vck")).using(project(":vck"))
//             substitute(module("at.asitplus.wallet:vck-openid")).using(project(":vck-openid"))
//             substitute(module("at.asitplus.wallet:vck-openid-ktor")).using(project(":vck-openid-ktor"))
//             substitute(module("at.asitplus.wallet:vck-rqes")).using(project(":vck-rqes"))
//             substitute(module("at.asitplus.wallet:openid-data-classes")).using(project(":openid-data-classes"))
//             substitute(module("at.asitplus.wallet:rqes-data-classes")).using(project(":rqes-data-classes"))
//         }
//     }
// }
