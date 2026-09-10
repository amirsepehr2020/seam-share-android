# SEAM Share Android

SEAM Share is a local Android ↔ Windows sharing client.

## Build

The project uses Android Gradle Plugin 8.7.3 and Gradle 8.9 with JDK 17.

In Android Studio, open this repository and allow Gradle to sync using the configured Gradle 8.9 distribution. CI also builds the debug APK on every push/PR.

## Default receive location

Incoming files are stored in `Downloads/SEAM Share` by default. The app can persist a user-selected folder through Android's Storage Access Framework.
