# kotlinx.serialization: keep the generated serializer lookups for our models.
-keepclassmembers class dev.mjablonski.rewordme.models.** {
    *** Companion;
}
-keepclasseswithmembers class dev.mjablonski.rewordme.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}
