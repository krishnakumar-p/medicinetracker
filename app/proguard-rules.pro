# MediShelf release rules.
# Most libraries (Room, Hilt, Compose, kotlinx.serialization, Navigation) ship consumer rules.
# Module-specific keeps are added alongside the code that needs them.

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- Navigation (type-safe routes) -------------------------------------------------------
# Enum route arguments are resolved by class name at runtime (Class.forName of the serial
# name), so enums used in @Serializable routes must keep their names.
-keepnames enum com.medishelf.**.navigation.** { *; }

# --- ML Kit (bundled models) ------------------------------------------------------------
# ML Kit discovers its components reflectively via no-arg constructors of these registrars;
# without this, barcode scanning and text recognition fail in release builds only.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keepclassmembers class com.google.mlkit.**.*Registrar { <init>(); }
