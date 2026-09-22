# Auralis 1.3.0

- **Softer motion:** Home rows settle gently, cards and the play button give subtle press feedback, navigation and color changes ease in and out, and Now Playing, the mini-player, lyrics, and album art transition more smoothly.
- **More stable external-EQ volume:** ReplayGain now uses Android's `DynamicsProcessing` input gain when available instead of hard-setting player volume at track transitions. This is intended to prevent skip-volume jumps with Poweramp Equalizer DVC.
- External equalizers now receive audio-effect control-session lifecycle broadcasts. Devices without `DynamicsProcessing` use a short smooth volume ramp as a fallback.

Download `Auralis-1.3.0.apk` below. Android 8.0 or later is required. The APK uses the same signing certificate as 1.2.0 for in-place updates.

Device testing, including Poweramp DVC validation, was not performed.

Validation: **37 unit tests passed**, lint passed with 0 errors and 18 warnings, and the signed APK build succeeded. APK signing was verified against the established Android Debug certificate.

SHA-256: `9e25bc21d7d2b4dae8b8335472bac8a86bb798f758b5f4eb2c2af24ff19d5d22`
