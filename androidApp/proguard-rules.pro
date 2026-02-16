# Studio Camera ProGuard Rules

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Studio Camera models
-keep,includedescriptorclasses class com.studiocamera.**$$serializer { *; }
-keepclassmembers class com.studiocamera.** {
    *** Companion;
}
-keepclasseswithmembers class com.studiocamera.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Koin
-keep class org.koin.** { *; }
