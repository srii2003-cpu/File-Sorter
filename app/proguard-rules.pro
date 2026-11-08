# Release builds never log (no OCR text, paths or ID numbers can leak to logcat).
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# SQLCipher is called from native code.
-keep class net.zetetic.database.** { *; }
