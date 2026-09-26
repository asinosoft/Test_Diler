import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.asinosoft.cdm"
    compileSdk = 37

    // Автоматический инкремент номера сборки
    val versionPropsFile = file("version.properties")
    if (versionPropsFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropsFile))
        val name = versionProps["VERSION_NAME"]
        val code = (versionProps["VERSION_CODE"] as String).toInt() + 1
        versionProps["VERSION_CODE"] = code.toString()
        versionProps.store(versionPropsFile.writer(), null)

        defaultConfig {
            applicationId = "com.asinosoft.cdm"
            minSdk = 24
            compileSdk = 37
            targetSdk = 37
            versionCode = code
            versionName = "${name}.${code}"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            base.archivesName = "$applicationId@$versionName"
        }
    } else {
        throw GradleException("Could not read version.properties!")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }

            applicationIdSuffix
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel)
    // Removed Coil dependencies until environment stabilizes
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // CameraX & ML Kit Barcode Scanning for QR
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.barcode.scanning)
    implementation(libs.libphonenumber)

    // Ads and analytics
    implementation(libs.google.services)
    implementation(libs.yandex.mobileads)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}