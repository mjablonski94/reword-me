plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:models"))
    implementation(libs.jna)
    implementation(libs.jna.platform)
}
