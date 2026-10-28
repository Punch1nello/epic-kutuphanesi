plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val updateManifestUrl = providers.gradleProperty("epicUpdateManifestUrl").orNull.orEmpty()
val escapedUpdateManifestUrl = updateManifestUrl.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.epiclibrary.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.epiclibrary.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "1.0.5"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$escapedUpdateManifestUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        
    debugImplementation("androidx.compose.ui:ui-tooling")
}

