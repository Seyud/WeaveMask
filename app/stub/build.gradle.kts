plugins {
    id("com.android.application")
    alias(libs.plugins.lsparanoid)
}

lsparanoid {
    seed = if (RAND_SEED != 0) RAND_SEED else null
    includeDependencies = true
    classFilter = { true }
}

android {
    namespace = "io.github.seyud.weave"
    enableKotlin = false

    val canary = !Config.version.contains(".")
    val base = "https://github.com/Seyud/WeaveMask/releases/download/"
    val url = base + "v${Config.version}/app-release.apk"
    val canaryUrl = base + "canary-${Config.versionCode}/"

    defaultConfig {
        applicationId = "io.github.seyud.weave"
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "APK_URL", "\"$url\"")
        buildConfigField("int", "STUB_VERSION", Config.stubVersion)
    }

    buildTypes {
        release {
            if (canary) buildConfigField("String", "APK_URL", "\"${canaryUrl}app-release.apk\"")
            proguardFiles("proguard-rules.pro")
            isMinifyEnabled = true
        }
        debug {
            if (canary) buildConfigField("String", "APK_URL", "\"${canaryUrl}app-debug.apk\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

setupStubApk()

dependencies {
    implementation(project(":shared"))
}
