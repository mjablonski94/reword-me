// Layered clean architecture, dependencies pointing inward only:
//   core:models <- core:domain <- core:data      (pure -> rules -> IO)
//   core:models <- desktop:platform              (Win32 capabilities via JNA)
//   everything  <- desktop:app                   (Compose UI + composition root)
//   core:*      <- android:platform              (Keystore/filesDir capabilities)
//   core:* + android:arch + android:platform <- android:app   (Compose + Koin root)
//
// android:arch carries only presentation plumbing (the MVI store), so it knows
// nothing about the domain and the domain knows nothing about it.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
}
