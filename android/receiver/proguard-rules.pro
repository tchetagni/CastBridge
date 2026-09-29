# libVLC calls back into Java from native code
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**
# NanoHTTPD and the app's own HTTP API use reflection-free code, but keep the entry points small and explicit
-keep class castbridge.receiver.PlayerActivity { *; }
-dontwarn fi.iki.elonen.**
