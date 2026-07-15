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
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20230618")
}
