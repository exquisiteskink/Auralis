# Sonveil 1.3.7

This release fixes the Poweramp Equalizer DVC playback regression introduced with the Sonveil rebrand on the Galaxy Z Fold 6. Play and Pause now work without the full-volume burst or repeating audio reported in 1.3.6.

Sonveil remains the launcher, playback service, and media session name. The Android application label retains `Auralis` so an existing Poweramp profile continues to apply. Android's app settings may show that compatibility label.

Download `Sonveil-1.3.7.apk` below. Android 8.0 or later is required. It installs over Sonveil 1.3.6 and Auralis 1.3.5 without losing app data because the Android app ID and signing certificate are unchanged.

Validation: the previous Auralis 1.3.5 APK and Sonveil were compared on the same Galaxy Z Fold 6. The final Sonveil build completed five low-volume Play/Pause checks without a burst or repeated audio. Android unit tests and lint passed.
