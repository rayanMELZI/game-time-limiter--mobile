# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.rayan.gametimelimiter.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
