# libopencv_java4.so throws org.opencv.core.CvException through FindClass("org/opencv/core/CvException"),
# which R8 cannot see; without this the class is removed and native errors arrive as java.lang.Exception.
-keep class org.opencv.core.CvException { *; }
