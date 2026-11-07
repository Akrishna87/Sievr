# kotlinx.serialization: keep generated serializers for the snapshot and user data.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.akrishna87.sievr.**$$serializer { *; }
-keepclassmembers class io.github.akrishna87.sievr.** { *** Companion; }
-keepclasseswithmembers class io.github.akrishna87.sievr.** { kotlinx.serialization.KSerializer serializer(...); }

# PDFBox looks up fonts, CMaps and security handlers by name at run time.
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
