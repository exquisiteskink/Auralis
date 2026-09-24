# Auralis 1.3.4

This update hardens playback with Poweramp Equalizer Direct Volume Control (DVC):

- Mutes before seeks, scrubbing, queue replacement, pause/resume, and playback retry paths that may recreate an Android AudioTrack.
- Waits for Media3 to report the new AudioTrack before starting the seek settle delay, then ramps volume back up.
- With Poweramp Equalizer or another known external EQ installed, pauses at automatic song boundaries and advances while muted. This adds a brief gap and disables overlapping crossfade on that route.
- Updates Playback settings text to describe the external EQ behavior.

Download `Auralis-1.3.4.apk` below. Android 8.0 or later is required. It uses the same signing certificate as 1.3.3 for in-place updates.

Validation: 59 unit tests passed; Android lint reported 0 errors and 18 warnings; debug and release APKs assembled. Physical-device testing with Poweramp Equalizer DVC was not available in this environment.
