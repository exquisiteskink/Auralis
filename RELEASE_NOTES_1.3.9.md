# Sonveil 1.3.9

This release makes playback queues easier to control and playlists easier to find.

- Shuffle keeps the song already playing at the top and shuffles every other queued song below it. The current stream and position continue without restarting.
- The Queue sheet opens and closes reliably with swipes, including fast downward gestures. A visible Up next control also opens it.
- Home playlists now appear as text chips, so they work well even when your server has no playlist artwork. The duplicate Continue shelf is gone.
- The README now includes screenshots captured on a Galaxy Z Fold 6.

Install `Sonveil-1.3.9.apk` on Android 8.0 or later. This version upgrades Sonveil 1.3.8 in place and keeps its app data. Sonveil 1.3.7 and earlier used a different Android package and remain separate installs.

Validation: 72 unit tests passed, Android lint reported no errors, and the signed APK installed over an existing Sonveil build on a Galaxy Z Fold 6. Shuffle, Queue gestures, Home, and playlist navigation were checked on-device.
