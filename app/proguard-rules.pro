# Diagnostic output — inspect build/outputs/mapping/release/ after
# each build to see exactly what R8 kept, removed, and why.
-printconfiguration build/outputs/mapping/release/full-r8-config.txt
-printseeds build/outputs/mapping/release/seeds.txt
-printusage build/outputs/mapping/release/usage.txt

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Moshi Rules to prevent JSON deserialization failures under minification
-keep class * {
    @com.squareup.moshi.Json class *;
    @com.squareup.moshi.JsonQualifier class *;
}
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class **_JsonAdapter { *; }

# Retrofit Rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Room Database Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.limit.annotations.RestrictTo
-keep class * extends androidx.room.migration.Migration
-keep class * extends androidx.room.RoomDatabase$Callback

# Suppress R8/ProGuard warnings for missing classes that are not used at runtime
-dontwarn com.google.api.client.http.**
-dontwarn java.lang.management.**
-dontwarn org.joda.time.**
-dontwarn io.ktor.**

# Keep all classes and members in the application and core packages from being stripped/obfuscated
-keep class com.inscopelabs.abx.server.** { *; }
-keep interface com.inscopelabs.abx.server.** { *; }
-keep enum com.inscopelabs.abx.server.** { *; }
-keep class com.inscopelabs.abx.server.boot.** { *; }

# WorkManager / AndroidX Startup Rules
# WorkManager and androidx.startup use reflection to discover their
# own Configuration.Provider / Initializer classes; without these
# rules R8 can strip or rename internals they depend on by exact
# name, causing an immediate, stacktrace-less crash at process
# startup (before Application.onCreate()).
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }
-keep class * extends androidx.startup.Initializer {
    public <init>();
}
-keepclassmembers class * implements androidx.work.Configuration$Provider {
    public *;
}
-dontwarn androidx.work.**

# ===== Anti-silent-killer hardening pass =====
# AAPT2/R8 already auto-keep real manifest components (Activity,
# Service, Receiver, Provider declared via android:name on a
# component tag) — do NOT add manual rules for those, it's
# redundant. The rules below target genuine reflection paths that
# are NOT auto-protected.

# androidx.startup: Initializer classes are discovered via
# <meta-data android:name="..."> STRING values inside
# InitializationProvider's manifest entry, not as a component tag
# — this reflection path is NOT covered by AAPT2's auto-keep.
-keep class * extends androidx.startup.Initializer {
    public <init>();
}
-keep class androidx.startup.InitializationProvider { *; }

# WorkManager: Configuration.Provider is discovered via an
# `application is Configuration.Provider` check — safe under R8
# on its own — but WorkManager's internal Room-backed work
# database and its own Worker-class-by-name lookup (used when
# resuming persisted work after process death) both rely on
# reflection separate from the app's own Room rules above.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# Kotlin coroutines: Continuation/suspend-function machinery and
# CoroutineExceptionHandler service-loader discovery.
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.**

# Compose: bundled consumer rules normally handle this, but keep
# Compose runtime's reflection-based slot-table/snapshot classes
# explicitly to rule this out as a variable.
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# zxing: pure-Java, no reflection, but the MultiFormatWriter's
# internal encoder lookup uses a switch-on-enum pattern R8 can
# sometimes miscompile in older versions — keep defensively.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

