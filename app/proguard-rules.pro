# kotlinx.serialization models (library ships its own rules; these keep saved history and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.talkctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.talkctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.talkctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Google-free: Guava's listenablefuture jar is excluded from the build (see app/build.gradle.kts).
# AndroidX's concurrent-futures still names its interface in classes TalkCtrl never uses.
-dontwarn com.google.common.util.concurrent.ListenableFuture

# Release builds drop verbose, debug and info logging (from the app and its libraries); warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}
