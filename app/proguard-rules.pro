# Project-specific R8/ProGuard rules. Library rules (Room, SQLCipher, ...)
# are added here as those dependencies are introduced.

# SQLCipher: native code calls back into these classes via JNI.
-keep class net.zetetic.database.** { *; }
