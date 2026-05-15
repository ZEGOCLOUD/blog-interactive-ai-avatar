# Interactive AI Avatar - Android Client

An Android (Kotlin) application that allows users to converse with a digital human (AI Avatar) through voice input. The AI Avatar responds in real-time with synchronized lip movements and facial expressions, rendered as a live video stream via ZEGO Express SDK.

## Environment Requirements

- Android Studio Hedgehog (2023.1.1) or higher
- Android SDK 24+ (Android 7.0+)
- Kotlin 2.0+
- JDK 11+

## Configuration

Edit `local.properties` in the project root:

| Variable | Description |
|----------|-------------|
| `ZEGO_APP_ID` | ZEGO application ID. Get from [ZEGO Console](https://console.zego.im/) |
| `ZEGO_API_BASE_URL` | Backend server URL. Use `10.0.2.2` for emulator (maps to host localhost); use computer LAN IP for real devices |

```properties
ZEGO_APP_ID=your_app_id_here
ZEGO_API_BASE_URL=http://10.0.2.2:3000
```

The AppID also falls back to the `ZEGO_APPID` environment variable if `local.properties` is not configured.

## Build and Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or run directly from Android Studio
```

Make sure the server is running before starting the app:
```bash
cd ../server
npm install
npm run dev
```

## Source Code Structure

```
app/src/main/
├── java/com/example/aiaiavatardemo/
│   ├── LoginActivity.kt         # Login page (username input + navigation)
│   └── MainActivity.kt          # Main page with ALL SDK logic (single-file principle)
├── res/
│   ├── layout/
│   │   ├── activity_login.xml   # Login screen layout
│   │   └── activity_main.xml    # Main screen (video + controls)
│   ├── values/
│   │   ├── strings.xml          # App strings
│   │   ├── colors.xml           # Color definitions
│   │   └── themes.xml           # App theme
│   └── xml/                     # Backup and data extraction rules
└── AndroidManifest.xml          # Permissions and activity declarations
```

## Core Flow

```
Login (enter username) -> Start Conversation -> Register Agent -> Create Instance -> Get Token -> Create Engine -> Login Room -> Publish Audio -> Play Agent Stream -> Voice Interaction -> End Conversation -> Delete Instance -> Logout Room
```

All SDK calls are made directly in `MainActivity.kt` following the "single-file" principle -- no Manager/Service/Wrapper classes.

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `im.zego:express-video` | 3.17.0 | ZEGO Express SDK (RTC) |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client for server API |
| `com.google.code.gson:gson` | 2.10.1 | JSON parsing |
| `com.google.android.material:material` | 1.11.0 | Material Design UI components |