# Proguard rules for Ajaz×tiktok

# SnakeYAML
-keepclassmembers class * {
    @org.yaml.snakeyaml.constructor.* <methods>;
}
-keep class org.yaml.snakeyaml.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Models
-keep class com.ajaz.tiktok.core.parser.** { *; }
-keepclassmembers class com.ajaz.tiktok.core.parser.** { *; }
