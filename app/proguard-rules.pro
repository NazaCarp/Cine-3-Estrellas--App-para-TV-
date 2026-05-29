# Proguard rules for Cine 3 Estrellas App

# Keep Ktor and serialization classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep ExoPlayer/Media3 classes if minified
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
