plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.seyud.weave.test"

    defaultConfig {
        applicationId = "io.github.seyud.weave.test"
        versionCode = 1
        versionName = "1.0"
        proguardFile("proguard-rules.pro")
    }

    buildTypes {
        release {
            optimization.enable = true
            isShrinkResources = false
        }
    }
}

setupTestApk()

dependencies {
    implementation(libs.test.runner)
    implementation(libs.test.rules)
    implementation(libs.test.junit)
    implementation(libs.test.uiautomator)
}
