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

include(
    ":core:models",
    ":core:domain",
    ":core:data",
    ":desktop:platform",
    ":desktop:app"
)
