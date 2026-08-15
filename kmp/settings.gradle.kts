pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "rewordme-kmp"

// AGP demands an SDK at configuration time, but the desktop is built on
// machines that only carry a JDK (the Windows release box among them), so
// everything Android activates only when an SDK is actually discoverable.
// Fresh desktop-only clones and package-windows.ps1 keep working unchanged.
val localProperties = file("local.properties")
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProperties.isFile && localProperties.readLines().any { it.trim().startsWith("sdk.dir") })

gradle.beforeProject {
    extra["hasAndroidSdk"] = hasAndroidSdk
}

include(
    ":core:models",
    ":core:domain",
    ":core:data",
    ":desktop:platform",
    ":desktop:app"
)

if (hasAndroidSdk) {
    include(
        ":android:arch",
        ":android:platform",
        ":android:app"
    )
}
