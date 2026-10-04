# zxing-cpp's native code looks up its Kotlin classes, fields and constructors
# by name over JNI, so R8 must not rename or remove any of them.
-keep class zxingcpp.** { *; }
