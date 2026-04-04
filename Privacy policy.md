# Privacy Policy

Last updated: April 4, 2026

## Overview

Glyph HA Integration ("the app") is an Android application that connects your Nothing phone Glyph Matrix to your Home Assistant sensor data.

This policy explains what data the app processes, where data is stored, and how data is used.

## Data the App Processes

The app may process the following information provided by you:

- Home Assistant base URL
- Home Assistant long-lived access token
- Home Assistant sensor entity IDs linked in the app
- Sensor values/states retrieved from your Home Assistant instance
- Optional custom icon pixel data created in the app

## How Data Is Used

Data is used only to provide app functionality, including:

- Connecting to your Home Assistant instance
- Fetching sensor states from `/api/states/{entity_id}`
- Rendering values/progress/icons on the phone Glyph Matrix
- Saving your app configuration locally on your device

## Data Storage

- Configuration and settings are stored locally on your device using Android SharedPreferences.
- The app does not require user account creation.
- The app developer does not receive your Home Assistant credentials through the app.

## Data Sharing and Sale

- The app does not sell personal data.
- The app does not share your data with advertising networks.
- The app does not include third-party analytics or tracking SDKs by default.

## Network Communication

The app communicates with your Home Assistant server using the URL and token you configure.

Data transmitted depends on your Home Assistant setup and network configuration.
You are responsible for securing your Home Assistant endpoint (for example, HTTPS and secure token handling).

## Permissions

The app uses permissions required for core functionality, including:

- Internet access (`android.permission.INTERNET`)
- Foreground service operation for background sync
- Nothing Glyph SDK permission (`com.nothing.ketchum.permission.ENABLE`)

## Data Retention and Deletion

- App data remains on your device until you clear app data or uninstall the app.
- You can remove configured sensors and clear custom icon data within the app.

## Children

The app is not directed to children under 13.

## Changes to This Policy

This Privacy Policy may be updated from time to time.
Any changes will be reflected by updating the "Last updated" date above.

## Contact

For privacy questions, contact:

- Email: your-email@example.com

Replace this email with your real support/privacy contact before publishing to Google Play.
