rootProject.name = "Wallet Relying Party"

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

rootProject.name = "Wallet Relying Party"
include("service")

//If we have a working composite build, use it!
if (File("../vck").isDirectory && File("../vck/build.gradle.kts").exists()) {
    logger.warn("\u001b[7m\u001b[1mDetected VC-K in ${File("../vck").absolutePath}.")
    logger.warn("Including VC-K as composite build.")
    logger.warn("If you do not want this, move the VC-K to another location!\u001b[0m")
    includeBuild("../vck")
}
