plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}
android {
    namespace = "com.fahad.easypeasy.engine"
    compileSdk = 35
    defaultConfig { minSdk = 26; ndk { abiFilters += "arm64-v8a" }; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.onnx)
    implementation(libs.mediapipe)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
