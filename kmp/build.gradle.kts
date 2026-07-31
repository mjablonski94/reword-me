// Layered clean architecture, dependencies pointing inward only:
//   core:models <- core:domain <- core:data      (pure -> rules -> IO)
//   core:models <- desktop:platform              (Win32 capabilities via JNA)
//   everything  <- desktop:app                   (Compose UI + composition root)
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
