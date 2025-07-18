plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.scovillo.playondlna"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.scovillo.playondlna"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.appcompat", "appcompat", "1.7.1")
    implementation("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.9.2")
    implementation("androidx.compose.ui", "ui", "1.8.3")
    implementation("androidx.compose.material3", "material3", "1.3.2")
    implementation("androidx.compose.ui", "ui-tooling-preview", "1.8.3")
    implementation("androidx.activity", "activity-compose", "1.10.1")
    implementation("androidx.lifecycle", "lifecycle-runtime-ktx", "2.9.2")

    implementation(files("libs/ffmpeg-kit-custom-androidzlib-gmp-gnutls-libiconv.main.arm-v7a-neon_arm64-v8a.aar"))
    implementation("com.arthenica", "smart-exception-java", "0.2.1")
    implementation(libs.nanohttpd)
    implementation("com.github.teamnewpipe", "NewPipeExtractor", "0.24.6")
    implementation("com.squareup.okhttp3", "okhttp", "4.12.0")
}


tasks.register("generateReadme") {
    group = "documentation"
    description =
        "Automatically generates a README.md with project details and Android configuration."

    doLast {
        val readmeFile = file("$projectDir/../README.md")
        val licenseFile = file("$projectDir/../LICENSE")
        val appName = project.findProperty("appName") as? String ?: project.name
        val android =
            project.extensions.findByName("android") as? com.android.build.gradle.internal.dsl.BaseAppModuleExtension
        val compileSdk = android?.compileSdkVersion ?: "Unknown"
        val minSdk = android?.defaultConfig?.minSdk ?: "Unknown"
        val targetSdk = android?.defaultConfig?.targetSdk ?: "Unknown"
        val appId = android?.defaultConfig?.applicationId ?: "Unknown"
        val versionCode = android?.defaultConfig?.versionCode ?: "Unknown"
        val versionName = android?.defaultConfig?.versionName ?: "Unknown"
        val licenseText =
            if (licenseFile.exists()) licenseFile.readText() else "  No license file found."
        val dependencies =
            configurations["implementation"].allDependencies.filter { it.group != null }
                .joinToString("\n") {
                    "  - ${it.group ?: ""}:${it.name}:${it.version ?: "unspecified"}"
                }
        val content = """
# $appName

📦 **Version:** $versionCode ($versionName)
⚙️ **Build-Tool:** Gradle ${gradle.gradleVersion}

## 🤖 Android Configuration

- **Application ID:** $appId  
- **Compile SDK:** $compileSdk  
- **Min SDK:** $minSdk  
- **Target SDK:** $targetSdk

## 📱 Description

Play Youtube videos on DLNA players (e.g. <a href="https://kodi.tv/">Kodi</a>) in your LAN!
Browse youtube in your favorite client and share the link to the PlayOnDlna app to play the video on a dlna player found in your LAN.
If the app serves you well, consider <a href="https://paypal.me/muemmelmaus">a donation</a> to support my efforts.
Happy streaming!

## 🎁 Donation

[![PayPal](https://www.paypalobjects.com/webstatic/icon/pp50.png)](https://paypal.me/muemmelmaus)

## 🛠️ Build Instructions

```bash
./gradlew build
```

## 📚 Dependencies

$dependencies
  - com.arthenica:ffmpeg-kit-custom:main

## 📄 License

${licenseText.trimIndent()}
        """.trimIndent()

        readmeFile.writeText(content)
    }
}