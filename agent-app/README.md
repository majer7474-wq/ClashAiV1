# Clash Vision Agent — Android split-screen capture prototype

A **separate Android application** that requests MediaProjection screen-capture permission, runs a foreground service, and counts captured frames. It is **not** yet an AI agent, autonomous controller, card recognizer, or learning system.

## Build
Use GitHub Actions workflow `build-agent.yml` (or Gradle 8.9 + JDK 17 locally). APK will be a workflow artifact if the build succeeds.

## Test
Install on Android 14, open in split screen with Clash Royale, tap 'Dopusti snimanje zaslona', approve **entire screen** capture (not just this app), and inspect notification frame counter. Depending on game/platform secure-surface policy, frames may be black. No bypass is attempted.

## Known limitations
- Screen-capture consent is required on every new capture session.
- Frame count alone does not prove the game content is visible.
- Split-screen changes dimensions and may trigger capture resize; current prototype needs resize handling.
- No AI inference, game recognition, input injection, or learning implemented.
- Captured frames are not uploaded or persisted.
