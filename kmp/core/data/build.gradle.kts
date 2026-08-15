plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
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
        // The HTTP provider clients are pure request builders/parsers, so they
        // live here; anything that spawns processes or touches the filesystem
        // stays behind the jvmMain gateways.
        commonMain.dependencies {
            api(project(":core:models"))
            api(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        // Tests live in jvmTest (there is no commonTest directory); wiring
        // them as commonTest would also compile them for androidUnitTest,
        // where the jvmMain-only classes they cover do not exist.
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        if (hasAndroidSdk) {
            getByName("androidMain").dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
        namespace = "dev.mjablonski.rewordme.data"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    }
}
