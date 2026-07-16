# Blocker+ APK - Build Instructions

## Build Command
```cmd
cmd /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot && cd /d "C:\Users\Admin\1 Code program\adblocker apk" && gradlew.bat assembleDebug --no-daemon"
```

## Output APK
`C:\Users\Admin\1 Code program\adblocker apk\app\build\outputs\apk\debug\app-debug.apk`

## Key Notes
- JDK 21 at `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot` (system default is JDK 26 which breaks Gradle 8.5)
- Android SDK at `C:\Users\Admin\AppData\Local\Android\Sdk`
- App: **Blocker+** (VPN-based ad blocker)
- Package: `com.adblocker.blockerplus`
- Uses R8 full mode (minification enabled)
- Min SDK 21 (Android 5.0+)
- **Under Maintenance** per README
