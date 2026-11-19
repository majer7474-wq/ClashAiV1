plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.clashvision.agent"
    compileSdk = 35
    defaultConfig { applicationId = "dev.clashvision.agent"; minSdk = 29; targetSdk = 34; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

android.sourceSets["main"].apply {
    java.srcDir(layout.buildDirectory.dir("firstlight/java"))
    assets.srcDir(layout.buildDirectory.dir("firstlight/assets"))
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
}
