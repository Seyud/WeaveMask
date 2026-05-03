plugins {
    alias(libs.plugins.android.application)
}

setupCommon()

android {
    namespace = "io.github.seyud.weave"
    enableKotlin = false

    buildTypes {
        release {
            isShrinkResources = false
        }
    }
}
