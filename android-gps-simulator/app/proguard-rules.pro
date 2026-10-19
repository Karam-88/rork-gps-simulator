# Release R8 configuration for GPS Simulator.
# Keep model classes and their members for compatibility with persisted JSON
# (saved places, routes, settings and active mock sessions).
-keep class com.rork.gpssimulator.data.model.** { *; }
-keep class com.rork.gpssimulator.data.AppSettings { *; }

# Kotlin Serialization's generated serializers are used for local data imports
# and persisted settings. Keep their metadata to prevent obfuscation surprises.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
