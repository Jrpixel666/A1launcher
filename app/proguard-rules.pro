# Keep Glance widget receivers/providers and kotlinx.serialization models reachable via reflection.
-keep class com.a1launcher.widget.** { *; }
-keepclassmembers class com.a1launcher.model.** { *; }
-dontwarn org.slf4j.**
