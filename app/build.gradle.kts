plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.scovillo.playondlna"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.scovillo.playondlna"
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
    implementation("androidx.core", "core-splashscreen", "1.0.1")
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

    implementation("io.github.scovillo", "ffmpeg-kit-playondlna", "main")
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
        val appName = project.findProperty("appName") as? String ?: project.name
        val android =
            project.extensions.findByName("android") as? com.android.build.gradle.internal.dsl.BaseAppModuleExtension
        val compileSdk = android?.compileSdkVersion ?: "Unknown"
        val minSdk = android?.defaultConfig?.minSdk ?: "Unknown"
        val targetSdk = android?.defaultConfig?.targetSdk ?: "Unknown"
        val appId = android?.defaultConfig?.applicationId ?: "Unknown"
        val versionCode = android?.defaultConfig?.versionCode ?: "Unknown"
        val versionName = android?.defaultConfig?.versionName ?: "Unknown"
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
If the app serves you well, consider a donation to support my efforts.

The app is built entirely on free software libraries.
All dependencies are compatible with the GNU GPLv3 license.
The app itself is licensed under the GNU GPLv3. See the 
[THIRD_PARTY_LICENSES.md](https://github.com/scovillo/playondlna/blob/main/THIRD_PARTY_LICENSES.md) 
file in the sourcerepository for full license information.

❤️ Happy streaming! ❤️

## 🎁 Donation

[![GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-❤️-pink?logo=github&style=flat-square)](https://github.com/sponsors/scovillo)

[![PayPal](https://www.paypalobjects.com/webstatic/icon/pp50.png)](https://paypal.me/muemmelmaus)

## 🛠️ Build Instructions

```bash
./gradlew build
```

## 📚 Dependencies

$dependencies
  - com.arthenica:ffmpeg-kit-custom:main

## 📄 License

PlayOnDlna - An Android application to play media on dlna devices
Copyright (C) 2025 Lukas Scheerer

Licensed under the GNU General Public License v3.0

You should have received a copy of the GNU GPL v3 in the [LICENSE](https://github.com/scovillo/playondlna/blob/main/LICENSE)
file along with this program. If not, see <https://www.gnu.org/licenses/>
        """.trimIndent()

        readmeFile.writeText(content)
    }
}