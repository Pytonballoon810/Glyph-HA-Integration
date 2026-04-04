# Glyph HA Integration

Android starter app that links Home Assistant sensors to Nothing Phone Glyph Matrix output.

## What it does

- Stores your Home Assistant base URL and long-lived token.
- Lets you link multiple Home Assistant sensor `entity_id` values.
- Supports 2 Glyph display modes per sensor mapping:
  - `Progress bar`: fills the matrix based on current value versus configured max value.
  - `Raw number`: prints the numeric/raw state as text.
- Polls Home Assistant every 5 seconds and rotates through linked sensors.

## SDK integration

This project already expects the Nothing SDK AAR at:

- `app/libs/glyph-matrix-sdk-2.0.aar`

The manifest includes required permission:

- `com.nothing.ketchum.permission.ENABLE`

## Build requirements

- Android Studio Koala+ (or newer)
- Android SDK 35
- Java 17
- A compatible Nothing device with Glyph Matrix support

## Run steps

1. Open project in Android Studio.
2. Let Gradle sync.
3. Connect/install on your Nothing phone.
4. Launch app.
5. Save Home Assistant URL and token.
6. Add one or more sensor mappings.
7. Tap `Start sync`.

## Home Assistant notes

- Create a long-lived token in Home Assistant user profile.
- Sensor API endpoint used by app: `/api/states/{entity_id}`
- For progress mode, non-numeric sensor values are ignored.

## Important behavior

- The app uses `setAppMatrixFrame(...)` as recommended for app-controlled rendering.
- Glyph Toy UI can override app output due to system display priority.
