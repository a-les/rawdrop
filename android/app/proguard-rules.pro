# Proguard rules for RawDrop
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.json.* <fields>;
}
