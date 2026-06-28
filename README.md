# Blocker+ - Android VPN Ad Blocker

> ⚠️ **Under Maintenance** — This app is currently being refactored and tested. Some features may be incomplete or subject to change.

A VPN-based ad blocker for Android that blocks ads, trackers, and adult/18+ content via local VPN traffic filtering with SNI sniffing and DNS interception.

## Features
- Blocks ads from major ad networks via DNS blackhole + TCP relay
- HTTPS ad blocking via TLS SNI sniffing
- Adult content filtering (domains + keywords)
- Gambling content filtering
- Custom blocklist (add your own domains)
- Whitelist (bypass blocking for specific domains)
- Per-app filtering (select which apps go through the VPN)
- Multiple DNS server presets (Google, Cloudflare, OpenDNS, Quad9, Custom)
- Block log with real-time stats
- Remote blocklist update (download from GitHub sources)
- Hosts file import
- No root required

## Requirements
- **Android**: 5.0 (API 21) or higher
- **Permissions**: VPN (for traffic filtering), Notification (for foreground service)
- **Storage**: ~7 MB

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
1. Creates a local VPN interface that captures all device traffic via `0.0.0.0/0` routing
2. Intercepts DNS queries (port 53) and checks domains against blocklists
3. For blocked domains: returns `0.0.0.0` (blackhole), preventing connection
4. For safe domains: forwards the DNS query to the selected DNS server
5. Intercepts TCP connections and extracts SNI from TLS ClientHello for HTTPS blocking
6. Relays non-blocked TCP traffic through a protected socket
7. Forwards UDP traffic (non-DNS) bidirectionally
8. Supports a local HTTP/HTTPS proxy (port 9898) for additional filtering

## Recent Changes

- Full TCP relay with SNI-based HTTPS blocking via TLS ClientHello parsing
- Per-app filtering (select which apps are routed through the VPN)
- Remote blocklist updater with 3 sources (anudeepND adservers, StevenBlack porn-only, StevenBlack gambling-only)
- Import hosts file support
- Whitelist management
- DNS server presets (All, Google, Cloudflare, OpenDNS, Quad9, Custom)
- Redesigned logo (AdGuard-style green shield)
- Block log with real-time auto-refresh
- Fixed `clearStats()` to only clear statistics (no longer wipes all preferences)
- Fixed per-app filtering logic (`addAllowedApplication` instead of `addDisallowedApplication`)
- Simplified UI — Settings-only layout
- Fixed notification to show static "Active" status
- Fixed ClassCastException on launch (ScrollView vs LinearLayout type mismatch)

## License
MIT
