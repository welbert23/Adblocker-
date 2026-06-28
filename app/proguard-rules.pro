-keepclassmembers class com.adblocker.AdBlockVpnService { *; }
-keep class com.adblocker.BlocklistDatabase { *; }
-keep class com.adblocker.DnsPacket { *; }
-keep class com.adblocker.ProxyServer { *; }
-keep class com.adblocker.BootReceiver { *; }

-keepclassmembers class * extends android.app.Service { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }
