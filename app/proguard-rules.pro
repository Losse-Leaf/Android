# Loose-Leaf Android Proguard/R8 Rules

# ---------- Kotlin / 元数据 ----------
-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable
-dontnote kotlinx.serialization.**
-dontnote kotlin.Metadata
-dontwarn kotlinx.serialization.**

# ---------- Kotlinx Serialization ----------
# 官方模板：保住每个 @Serializable 类的合成 Companion 与 serializer()
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.looseleaf.app.**$$serializer { *; }
-keepclassmembers class com.looseleaf.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.looseleaf.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 枚举按名称序列化
-keepclassmembers enum com.looseleaf.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Coil ----------
-dontwarn coil.**

# ---------- Tink (security-crypto 传递依赖) ----------
# 仅编译期注解，运行期不需要
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.**

# ---------- WorkManager ----------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.looseleaf.app.data.sync.OutboxSyncWorker { *; }

# ---------- 反射入口 ----------
-keep class androidx.core.content.FileProvider { *; }
