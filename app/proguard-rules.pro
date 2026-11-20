# ── Attributes needed by reflection-based serializers ──
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# ── DTO / data models (Gson) ──
-keep class com.openlauncher.app.data.** { *; }
-keep class com.openlauncher.app.model.** { *; }
-keep class com.openlauncher.app.mediastation.data.** { *; }
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * { @androidx.annotation.Keep *; }
-keep class com.openlauncher.app.ui.screen.PhotonPlace { *; }
-keep class com.openlauncher.app.ui.screen.ValhallaManeuver { *; }
-keep class com.openlauncher.app.ui.screen.ValhallaRoute { *; }
-keep class com.openlauncher.app.ui.screen.RouteOptionType { *; }
-keep class com.openlauncher.app.ui.screen.RoutePreviewOption { *; }
-keep class com.openlauncher.app.ui.screen.AutomotiveMapStyle { *; }
-keep class com.openlauncher.app.util.AppLanguageOption { *; }
-keep class com.openlauncher.app.util.AppLanguageManager { *; }

# ── Gson ──
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-dontwarn sun.misc.**

# ── Retrofit (if introduced) ──
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# ── kotlinx.serialization (if introduced) ──
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class **$$serializer { *; }
-dontwarn kotlinx.serialization.**

# ── OkHttp / Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Media3 / ExoPlayer / FFmpeg decoder (JNI) ──
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn androidx.media3.**

# ── MapLibre (JNI) ──
-keep class org.maplibre.** { *; }
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.mapbox.**

# ── Notification listener + boot receiver ──
-keep class com.openlauncher.app.service.MediaNotificationListenerService { *; }
-keep class com.openlauncher.app.receiver.ColdBootReceiver { *; }
-keep class com.openlauncher.app.MainActivity { *; }

# ── NewPipeExtractor & Rhino JS engine ──
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-keep class org.mozilla.** { *; }
-dontwarn org.mozilla.**
-dontwarn javax.annotation.**
-dontwarn java.beans.**

# -- NanoJSON (NewPipeExtractor internal JSON parser) --
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
