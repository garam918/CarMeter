# Release builds drop verbose, debug and info logs, including the Vehicle SDK's own,
# which print property names and values (docs/sdk-notes.md 1.8). Warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Vehicle SDK 2.3.2 parses IPC results with Gson TypeToken subclasses and checks permissions
# through kotlin-reflect. Its bundled consumer rules do not cover the Gson part under R8 full
# mode: release builds failed with "TypeToken must be created with a type argument" and no
# feature started (docs/sdk-notes.md 13). Keep the SDK as shipped and the generic signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class ai.pleos.playground.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
