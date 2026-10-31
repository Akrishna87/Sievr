# kotlinx.serialization: keep generated serializers for the snapshot and user data.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.akrishna87.sievr.**$$serializer { *; }
-keepclassmembers class io.github.akrishna87.sievr.** { *** Companion; }
-keepclasseswithmembers class io.github.akrishna87.sievr.** { kotlinx.serialization.KSerializer serializer(...); }
