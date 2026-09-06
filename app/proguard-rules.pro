# Roman Nepali Keyboard ProGuard Rules

# Keep the keyboard service
-keep class com.romannepali.keyboard.RomanNepaliIME { *; }

# Keep Android Input Method Service
-keep class android.inputmethodservice.InputMethodService { *; }

# Keep support library
-keep class android.support.** { *; }
-keep class androidx.** { *; }

# Keep Material Components
-keep class com.google.android.material.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
