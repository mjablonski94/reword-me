plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary) apply false
}

// The android target exists only where an SDK does (see settings.gradle.kts).
val hasAndroidSdk = extra["hasAndroidSdk"] as Boolean
if (hasAndroidSdk) {
    apply(plugin = libs.plugins.androidLibrary.get().pluginId)
}

kotlin {
    jvmToolchain(21)
    jvm()
    if (hasAndroidSdk) androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:models"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
        namespace = "dev.mjablonski.rewordme.domain"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    }
}
