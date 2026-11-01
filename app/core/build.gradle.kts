plugins {
    id("com.android.library")
    kotlin("plugin.parcelize")
    alias(libs.plugins.moshi)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.wire)
}

setupCoreLib()

ksp {
    arg("room.generateKotlin", "true")
}

wire {
    kotlin {}
}

android {
    namespace = "io.github.seyud.weave.core"

    defaultConfig {
        buildConfigField("String", "APP_PACKAGE_NAME", "\"io.github.seyud.weave\"")
        buildConfigField("int", "APP_VERSION_CODE", "${Config.versionCode}")
        buildConfigField("String", "APP_VERSION_NAME", "\"${Config.version}\"")
        consumerProguardFile("proguard-rules.pro")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        // 与 :apk 的 setupAppCommon 保持同一翻译策略：本项目的翻译由社区
        // /上游同步流入，缺失语言回退英文是既定行为，不在此翻译。
        disable += "MissingTranslation"
        // TrustAllX509TrustManager 仅命中 bcpkix 依赖内部的 JDK 类，
        // 不是本项目代码，无法在源码侧修复
        disable += "TrustAllX509TrustManager"
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.jdk.libs)

    api(libs.timber)
    api(libs.markwon.core)
    implementation(libs.bcpkix)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.wire.runtime)

    api(libs.libsu.core)
    api(libs.libsu.service)
    api(libs.libsu.nio)

    implementation(libs.hiddenapibypass)
    implementation(libs.rikka.parcelablelist)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.retrofit.scalars)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.core.ktx)
    implementation(libs.activity)
    implementation(libs.collection.ktx)
    implementation(libs.profileinstaller)

    // We also implement all our tests in this module.
    // However, we don't want to bundle test dependencies.
    // That's why we make it compileOnly.
    testImplementation(libs.junit)

    compileOnly(libs.test.junit)
    compileOnly(libs.test.uiautomator)
}
