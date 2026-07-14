plugins {
    id("com.android.application")
}

android {
    namespace = "at.asitplus.wallet.rp"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.asitplus.wallet.rp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.browser:browser:1.10.0")

    testImplementation("junit:junit:4.13.2")
}
