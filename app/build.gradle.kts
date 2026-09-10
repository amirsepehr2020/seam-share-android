plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "ir.redlighte.seamshare"; compileSdk = 35
    defaultConfig { applicationId = "ir.redlighte.seamshare"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
}

kotlin { jvmToolchain(17) }
android.buildFeatures { compose = true }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
