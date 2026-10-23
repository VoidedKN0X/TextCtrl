# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.textctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.textctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.textctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

