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

val vckVersion: String by extra

include("service")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    versionCatalogs {
        create("vck") {
            from("at.asitplus.wallet:vck-openid-versionCatalog:$vckVersion")
        }
    }
}


// //If we have a working composite build, use it!
// if (File("../vck/signum").isDirectory && File("../vck/signum/build.gradle.kts").exists()) {
//     logger.warn("\u001b[7m\u001b[1mDetected VC-K in ${File("../vck").absolutePath}.")
//     logger.warn("Including VC-K and Signum as composite build.")
//     logger.warn("If you do not want this, move the VC-K to another location!\u001b[0m")
//     includeBuild("../vck/signum") {
//         dependencySubstitution {
//             substitute(module("at.asitplus.wallet:indispensable")).using(project(":indispensable"))
//             substitute(module("at.asitplus.wallet:indispensable-jvm")).using(project(":indispensable"))
//             substitute(module("at.asitplus.signum:indispensable-josef")).using(project(":indispensable-josef"))
//             substitute(module("at.asitplus.signum:indispensable-josef-jvm")).using(project(":indispensable-josef"))
//             substitute(module("at.asitplus.signum:indispensable-cosef")).using(project(":indispensable-cosef"))
//             substitute(module("at.asitplus.signum:indispensable-cosef-jvm")).using(project(":indispensable-cosef"))
//             substitute(module("at.asitplus.signum:supreme")).using(project(":supreme"))
//             substitute(module("at.asitplus.signum:supreme-jvm")).using(project(":supreme"))
//         }
//     }
//     includeBuild("../vck") {
//         dependencySubstitution {
//             substitute(module("at.asitplus.wallet:vck")).using(project(":vck"))
//             substitute(module("at.asitplus.wallet:vck-jvm")).using(project(":vck"))
//             substitute(module("at.asitplus.wallet:vck-openid")).using(project(":vck-openid"))
//             substitute(module("at.asitplus.wallet:vck-openid-jvm")).using(project(":vck-openid"))
//             substitute(module("at.asitplus.wallet:vck-rqes")).using(project(":vck-rqes"))
//             substitute(module("at.asitplus.wallet:vck-rqes-jvm")).using(project(":vck-rqes"))
//             substitute(module("at.asitplus.wallet:vck-openid-ktor")).using(project(":vck-openid-ktor"))
//             substitute(module("at.asitplus.wallet:vck-openid-ktor-jvm")).using(project(":vck-openid-ktor"))
//             substitute(module("at.asitplus.wallet:openid-data-classes")).using(project(":openid-data-classes"))
//             substitute(module("at.asitplus.wallet:openid-data-classes-jvm")).using(project(":openid-data-classes"))
//             substitute(module("at.asitplus.wallet:dif-data-classes")).using(project(":dif-data-classes"))
//             substitute(module("at.asitplus.wallet:dif-data-classes-jvm")).using(project(":dif-data-classes"))
//             substitute(module("at.asitplus.wallet:vck-rqes")).using(project(":vck-rqes"))
//             substitute(module("at.asitplus.wallet:vck-rqes-jvm")).using(project(":vck-rqes"))
//             substitute(module("at.asitplus.wallet:rqes-data-classes")).using(project(":rqes-data-classes"))
//             substitute(module("at.asitplus.wallet:rqes-data-classes-jvm")).using(project(":rqes-data-classes"))
//         }
//     }
// }
