# Auralis 1.3.5

This update fixes brief full-volume bursts with Poweramp Equalizer Direct Volume Control (DVC):

- Keeps the external EQ session active through Pause, Resume, seeks, song changes, and album changes.
- Preserves natural quick resumes and song transitions while guarding cold starts and long pauses.
- Uses the same correct album cover in Now Playing, Home, and the album page, and avoids blank artwork flashes during song changes.

Download `Auralis-1.3.5.apk` below. Android 8.0 or later is required. It uses the same signing certificate as 1.3.4 for in-place updates.

Validation: 63 unit tests passed; Android lint reported 0 errors and 18 warnings. Listening tests on a Galaxy Z Fold 6 with Poweramp Equalizer DVC enabled found no bursts during quick Pause/Resume, seeks, manual and automatic song changes, album changes, or Resume after a 15-second pause at low speaker volume. Overlapping crossfade remains disabled when a known external EQ is installed.
