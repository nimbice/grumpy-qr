import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from keystore.properties (local builds) or environment
// variables (CI). Neither is ever committed. Without them, release builds are
// simply left unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    keystoreProps.getProperty(property) ?: System.getenv(env)

android {
    namespace = "io.github.nimbice.grumpyqr"
    compileSdk = 37

    defaultConfig {
        // The Play Store package name. It can never change after the first upload.
        applicationId = "io.github.nimbice.grumpyqr"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "GRUMPY_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "GRUMPY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "GRUMPY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "GRUMPY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Don't embed Google's encrypted dependency report in APKs/bundles. It is
    // opaque to users and F-Droid rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Dependabot handles version bumps; lint shouldn't fail builds over them.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.zxing.cpp)

    testImplementation(libs.junit)
}
