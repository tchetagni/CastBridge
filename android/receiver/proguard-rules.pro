# libVLC calls back into Java from native code
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**
# NanoHTTPD and the app's own HTTP API use reflection-free code, but keep the entry points small and explicit
-keep class castbridge.receiver.PlayerActivity { *; }
-dontwarn fi.iki.elonen.**
# Apache MINA SSHD (SSH administration): loads algorithms, factories and providers by name
-keep class org.apache.sshd.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn org.apache.sshd.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.slf4j.**
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.**
-dontwarn org.bouncycastle.**
