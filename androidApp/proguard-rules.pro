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

# Keep Studio Camera serializable models
-keep,includedescriptorclasses class com.studiocamera.**$$serializer { *; }
-keepclassmembers class com.studiocamera.** {
    *** Companion;
}
-keepclasseswithmembers class com.studiocamera.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor client (engine resolved at runtime)
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }
-dontwarn io.ktor.**

# Koin (reflection-based DI)
-keep class org.koin.core.** { *; }
-keep class org.koin.mp.** { *; }
-dontwarn org.koin.**

# Decompose (serialized navigation state)
-keep class com.arkivanov.decompose.router.** { *; }
-keep class com.arkivanov.essenty.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# OkHttp (Ktor engine)
-dontwarn okhttp3.**
-dontwarn okio.**

# Strip debug logs in release (Kermit uses standard logging)
-assumenosideeffects class co.touchlab.kermit.Logger {
    public void d(...);
    public void v(...);
}
