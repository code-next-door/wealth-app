# Project-specific R8/ProGuard rules. Library rules (Room, SQLCipher, ...)
# are added here as those dependencies are introduced.

# SQLCipher: native code calls back into these classes via JNI.
-keep class net.zetetic.database.** { *; }

# PdfBox-Android: optional JPEG 2000 support isn't bundled.
-dontwarn com.gemalto.jp2.**
