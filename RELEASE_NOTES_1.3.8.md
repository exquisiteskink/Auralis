# Sonveil 1.3.8

This release changes the Android package to `app.sonveil.music` and sets the application label to Sonveil. It does not install over Sonveil 1.3.7 or Auralis. Those builds used `app.auralis.music` and stay on the phone with their accounts and downloads. Sign in again on this package. The APK is signed with the same certificate as 1.3.7.

The equalizer is a vertical 10-band or a parametric equalizer, each with a preamp. Search AutoEQ loads one of 8,849 headphone measurements into the selected mode. The curve is saved for the connected output and applied again when that output connects.

Playback no longer stops with an equalizer runtime error. Track changes no longer force a fade-in when Poweramp Equalizer or Wavelet is not installed. The sleep timer survives seek and the next track. An artist’s album grid opens, the library can be browsed beyond the home shelves, and an interrupted download can continue.

Download `Sonveil-1.3.8.apk` below. Android 8.0 or later is required.

Validation: unit tests passed. On a Galaxy Z Fold 6 the debug build loaded 8,849 AutoEQ measurements and played a track without the previous equalizer error.
