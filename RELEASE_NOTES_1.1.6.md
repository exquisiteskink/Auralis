# Auralis 1.1.6

Auralis 1.1.6 improves the Now Playing spacing and reorganizes Home.

- The seek bar now sits beneath the album artwork, with track details and playback controls grouped closely below it. Extra space remains below the controls (#9).
- Larger play/pause, skip, shuffle, and repeat icons (#9).
- Home now starts with larger playlist cards, followed by Continue, recently played and recently added albums, compact favorite tracks, artists in rotation, and genres (#7).

## Release checklist

- Set versionName to 1.1.6 and versionCode to 18 in app/build.gradle.kts.
- Build and validate: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Use the existing local signing keystore that signed prior APKs; a new generated key will not permit an in-place update.
- Test Now Playing layout and controls on a device, including compact screens and larger font settings.
- Attach the resulting APK as `Auralis-1.1.6.apk` to GitHub release `v1.1.6`.
- Date the changelog entry and update README install/version references when publishing.

Status: release preparation only; version bump pending, APK not built or attached, release not published.
