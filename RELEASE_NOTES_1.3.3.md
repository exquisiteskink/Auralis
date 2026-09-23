# Auralis 1.3.3

This release includes the changes from pull requests #27–#31:

- Saved sessions open the app after restoration without a Login flash; transient server errors keep the session available.
- Search shows suggestions and recent queries. Tapping a song offers Play, Go to artist, and Go to album when available.
- Playback can retry transient connection errors in the same queue and refreshes stream URLs for upcoming songs.
- Media transitions wait briefly while external equalizers bind. Dual-player crossfade is disabled when Poweramp Equalizer or a known external EQ is installed.

Download `Auralis-1.3.3.apk` below. Android 8.0 or later is required. The APK uses the same signing certificate as 1.3.2 for in-place updates.

Validation: 59 unit tests passed; Android lint reported 0 errors and 18 warnings; debug and release APKs assembled. Device playback and Poweramp Equalizer testing were not performed.
