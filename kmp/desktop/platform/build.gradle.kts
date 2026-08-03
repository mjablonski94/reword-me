plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:models"))
    api(project(":core:domain"))
    implementation(libs.jna)
    implementation(libs.jna.platform)
    testImplementation(libs.kotlin.test)
}
