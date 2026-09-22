# Auralis 1.3.2

This bugfix release includes two fixes:

- Restores the flat Now Playing seek bar and removes the unfinished waveform UI (#25).
- Uses one audio session throughout playback and crossfade, and briefly mutes first playback while Poweramp Equalizer binds. This is intended to prevent DVC volume spikes during audio session changes (#26).

Download `Auralis-1.3.2.apk` below. Android 8.0 or later is required. The APK uses the same signing certificate as 1.3.1 for in-place updates.

Validation: 50 unit tests passed, Android lint reported 0 errors and 18 warnings, and debug and release APK builds succeeded. Device testing with Poweramp Equalizer was not performed.
