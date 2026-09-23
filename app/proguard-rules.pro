# 网关返回的 JSON 用 kotlinx.serialization 解析，模型类靠 @Serializable 生成序列化器，
# 不通过反射，因此不需要 keep 规则；但 kotlinx.serialization 自带的规则要保留。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 播放服务由系统按类名实例化。
-keep class com.sakura.music.core.player.MusicPlaybackService { *; }
