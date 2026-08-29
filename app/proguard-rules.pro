# Apache POI — keep names referenced by reflection
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.logging.log4j.**
-dontwarn javax.xml.**
-dontwarn org.openxmlformats.schemas.**

# ODF Toolkit
-keep class org.odftoolkit.** { *; }
-dontwarn org.odftoolkit.**

# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.**

# Compose
-keep class androidx.compose.** { *; }
