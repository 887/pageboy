# Phase I/J — Apache POI keep-rules for the DOCX (XWPF) + XLSX (XSSF)
# OOXML pair. Lifted from the centic9/poi-on-android shadow recipe and
# narrowed to the surface pageboy actually uses (we don't ship
# PowerPoint / HSLF / HSSF / HWPF — those are read-and-dropped from
# the APK).
#
# XmlBeans uses runtime reflection on its generated schema classes;
# naive shrinking removes them and the runtime then fails to instantiate
# schema bundles. The keep-rules preserve the schema bundles that
# XWPF (DOCX) + XSSF (XLSX) actually load.

# Apache POI core
-keep class org.apache.poi.** { *; }
-keepclassmembers class org.apache.poi.** { *; }

# XmlBeans + the generated schema bundles
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keepclassmembers class org.apache.xmlbeans.** { *; }

# OOXML schemas — keep the WordprocessingML + SpreadsheetML schemas
# (we read DOCX + XLSX); the other OOXML schemas (DrawingML for chart
# rasterising, PresentationML for PowerPoint) get dropped by R8.
-keep class org.openxmlformats.schemas.wordprocessingml.** { *; }
-keep class org.openxmlformats.schemas.spreadsheetml.** { *; }
-keep class org.openxmlformats.schemas.officeDocument.** { *; }
-keep class org.openxmlformats.schemas.drawingml.** { *; }

# Aalto-XML — POI delegates StAX through the three system properties
# we set in PageboyApplication.onCreate(); keep the factory impl classes
# so reflection-based discovery via the property doesn't fail at runtime.
-keep class com.fasterxml.aalto.** { *; }
-keep class com.fasterxml.aalto.stax.InputFactoryImpl { *; }
-keep class com.fasterxml.aalto.stax.OutputFactoryImpl { *; }
-keep class com.fasterxml.aalto.stax.EventFactoryImpl { *; }

# excel-streaming-reader (the active pjfanning fork).
-keep class com.github.pjfanning.xlsx.** { *; }
-keep class com.monitorjbl.xlsx.** { *; }

# POI scratchpad / Commons-Compress (ZIP I/O for OOXML packages).
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.collections4.** { *; }

# log4j-api shims — we excluded the real log4j artifact via Gradle but
# POI compiles against the interfaces; keep the shim classes if present.
-dontwarn org.apache.logging.log4j.**

# Strip PowerPoint + binary-XLS + binary-DOC surfaces explicitly. R8
# usually does this on its own but it's clearer to be explicit so the
# APK analyzer reads cleanly.
# (We don't reference these from pageboy code; R8 tree-shakes them.)

# POI's `java.awt.*` references (rare on the read path but present in
# the chart / drawing modules) — Android doesn't ship java.awt.
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Suppress warnings for transitive deps we exclude.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.osgi.framework.**
