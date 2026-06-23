# Blocker+ - Android VPN Ad Blocker

A VPN-based ad blocker for Android that blocks ads, trackers, and adult/18+ content via local DNS filtering.

## Features
- Blocks ads from major ad networks (DoubleClick, Google AdServices, etc.)
- Blocks adult/pornographic websites (500+ domains)
- Real-time toggle — turn adult blocking on/off without restarting VPN
- Always-on protection via VPN tunnel
- No root required
- Auto-start on boot

## Requirements
- **Android**: 5.0 (API 21) or higher
- **Permissions**: VPN (for traffic filtering), Notification (for foreground service)
- **Storage**: ~6 MB

## How to Use

### Install
1. Download the APK from Releases
2. Enable "Install from unknown sources" if needed
3. Open the app

### Setup
1. Open Blocker+
2. Tap **START**
3. Accept the VPN permission prompt
4. You'll see "Protected" — ad blocking is now active

### Toggle Adult Content Blocking
- The **Block Adult Content** switch is ON by default
- Toggle it OFF to allow adult sites while still blocking ads
- Changes apply immediately — no need to restart

## Build from Source

```bash
git clone https://github.com/welbert23/Adblocker-.git
cd Adblocker-
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## How It Works
1. Creates a local VPN interface that captures all device traffic
2. Intercepts DNS queries (port 53) before they leave the device
3. Checks queried domains against blocklists (ad + adult)
4. For blocked domains: returns `0.0.0.0` (blackhole), preventing connection
5. For safe domains: forwards the DNS query to real DNS servers (8.8.8.8, 1.1.1.1)
6. Also runs a local HTTP/HTTPS proxy (port 9898) for ad filtering

## License
MIT
