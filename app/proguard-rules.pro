# Room entities and DAOs
# Room uses reflection to instantiate these classes and access fields
-keep class com.neldasi.dafscanner.data.** { *; }
-keepclassmembers class com.neldasi.dafscanner.data.** { *; }

# Gson models
# These must be kept because Gson uses reflection to map JSON keys to field names.
# If R8 renames these fields, JSON parsing will fail.
-keep class com.neldasi.dafscanner.viewmodels.SearchItem { *; }
-keep class com.neldasi.dafscanner.extras.ScanStorage$PendingScan { *; }

# General ProGuard/R8 attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Kotlin Serialization (if used via reflection or for safety with @Serializable)
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Preserve line numbers for stack traces
-renamesourcefileattribute SourceFile

# Android XR / SceneCore
# These classes are provided by the system on XR devices.
-dontwarn com.android.extensions.xr.**
-dontwarn com.google.androidxr.**
-dontwarn com.google.imp.splitengine.**
