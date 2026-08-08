# Proguard rules for Cine 3 Estrellas Mobile App

# Keep Ktor and serialization classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep ExoPlayer/Media3 classes if minified
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep shared module classes if needed
-keep class com.cine3estrellas.** { *; }
