plugins {
    id("com.android.application")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization") version "2.3.20"
    alias(libs.plugins.compose.compiler)
}

setupMainApk()

android {
    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        proguardFile("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.jdk.libs)

    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.material)

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Navigation 3 + Miuix NavDisplay
    implementation(libs.navigation3.runtime)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigationevent.compose)
    implementation(libs.miuix.navigation3.ui)

    // Miuix
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shapes)

    // Backdrop (Liquid Glass)
    implementation(libs.backdrop)

    // WebKit (WebViewAssetLoader)
    implementation("androidx.webkit:webkit:1.13.0")
}
