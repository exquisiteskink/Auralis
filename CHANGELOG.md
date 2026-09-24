# Changelog

## 1.3.4 — 2026-09-23

### Fixed
- Guard seeks, queue replacement, pause/resume, and error recovery so audio stays muted while a new AudioTrack is prepared for Poweramp Equalizer DVC.
- Pause at automatic song boundaries when a known external EQ is installed, then advance and resume after the new AudioTrack settles. This introduces a brief gap and disables overlapping crossfade in that mode.
- Clarify the playback settings when the external EQ safety path is active.

### Validation
- 59 unit tests passed; Android lint reported 0 errors and 18 warnings; debug and release APKs assembled. Device testing with Poweramp Equalizer DVC was not available in this environment.

## 1.3.3 — 2026-09-22

### Added
- Search suggestions from `search3`, recent searches, and song actions for playback or navigation to the artist or album (#28, #29).

### Fixed
- Wait for saved-session restoration before showing Login; transient server failures retain the signed-in app shell (#27).
- Recover from transient playback connection errors with retry controls and refreshed stream URLs in the existing queue (#30).
- Mute during media transitions while the external equalizer binds; disable dual-player crossfade when Poweramp Equalizer or a known external EQ is installed (#31).

### Validation
- 59 unit tests passed; Android lint reported 0 errors and 18 warnings; debug and release APKs assembled. Device playback and Poweramp Equalizer testing were not performed.

## 1.3.2 — 2026-09-22

### Fixed
- Restored the flat Now Playing seek bar and removed the unfinished waveform UI (#25).
- Kept one audio session for the playback service lifetime, including crossfade, to avoid Poweramp Equalizer DVC volume spikes during session changes. First playback waits briefly while the external EQ binds (#26).

### Validation
- 50 unit tests passed; Android lint reported 0 errors and 18 warnings; debug and release APKs assembled. Device testing with Poweramp Equalizer was not performed.

## 1.3.1 — 2026-09-22

### Fixed
- Session restoration retries after transient network failures instead of leaving the app signed out until restart (#16).
- Offline downloads trust a valid response `Content-Length`, while retaining metadata-size validation when the response length is unavailable (#18).
- Album track lists tolerate duplicate server media IDs without Compose key collisions (#19).
- Sleep timers survive reboot, keep monotonic timing during the same boot, and stay synchronized with Settings when cleared (#20, #22).
- Crossfade now overlaps players without stealing audio focus, and releases the fading player's EQ before attaching EQ to the promoted player (#15, #17).
- Queue, playback position, shuffle, and repeat state restore after process death, including service-first notification, Bluetooth, and Android Auto resumption (#21).

### Validation
- Complete unit tests, Android lint, and release APK assembly checked before publication. Device and Android Auto head-unit tests were not run.

## 1.3.0 — 2026-09-22

### Added
- Softer motion throughout Home, navigation, Now Playing, and the mini-player: shared motion tokens, gentle list snap settling, card and play-button press feedback, smoother palette changes, and album-art crossfades (#11, #12, #13).

### Fixed
- ReplayGain now uses `DynamicsProcessing` input gain when available, keeping the player volume at unity and avoiding the hard volume jumps that could conflict with Poweramp Equalizer DVC on track changes (#10).
- External equalizers receive audio-effect control-session open/close broadcasts; platforms without `DynamicsProcessing` use a short smooth volume ramp as a fallback (#10).

### Validation
- Combined unit tests, Android lint, and APK build checked before publication. Device testing, including Poweramp DVC validation, was not run.

## 1.2.0 — 2026-09-21

### Added
- Offline album/playlist downloads, account-scoped local storage, and Home → Downloads for playback after an offline restart (#5).
- Sleep timer in Settings → Playback (15 / 30 / 45 / 60 minutes or end of track), with safe crossfade cancellation and item-boundary stopping (#4).
- Android Auto browsing for playlists, recently played albums, favorites, and recently added albums; preserves phone queues and duplicate playlist occurrences (#8).
- Bit depth alongside sample rate on Now Playing, using metadata for the current song and omitting unavailable values.
- Artwork override storage and rendering scaffold with observable revisions and collision-resistant file keys; picker and app wiring remain follow-up work (#1).

### Validation
- Combined unit tests, Android lint, and APK build checked before publication. Device and Android Auto head-unit tests were not run.

## 1.1.6 — 2026-09-21

### Fixed
- Removed the large empty gap in Now Playing: the seek bar now sits beneath the artwork, with track details and playback controls grouped closely below it (#9).

### Changed
- Enlarged the Now Playing playback controls (#9).
- Reorganized Home around larger playlist cards, Continue, recently played and recently added albums, compact favorite tracks, artists in rotation, and genres; removed the For you artist-mix section (#7).

## 1.1.5 — 2026-09-20

### Added
- Genre chips and recently played artists on Search when the query is blank and the search field is unfocused (#2).
- Album year and record label when supplied by the server (#3).

### Changed
- Enlarged Now Playing artwork, moved the close button to the top, and placed seek and playback controls lower on the screen (#6).

## 1.1.4 — 2026-09-19

### Changed
- Made the mini-player, now-playing, and queue transitions continuous and velocity-aware.
- Kept navigation and screen layout stable while the player moves, avoiding the mid-animation jump.
- Isolated playback-position updates and deferred queue rendering to reduce animation-frame work.

## 1.1.3 — 2026-09-19

### Added
- Multi-disc album support. Album pages now separate and label discs, keep disc and track order in the playback queue, and handle untagged tracks as disc 1.

## 1.1.2 — 2026-09-18

### Removed
- AutoEq-style headphone targets from the equalizer. The 10-band EQ and general presets remain.

## 1.1.1 — 2026-09-18

### Fixed
- Playback failed on 1.1.0. Custom EQ/ReplayGain audio processors sat in ExoPlayer’s sink and aborted original/hi-res streams. EQ now attaches to the audio session (DynamicsProcessing / platform equalizer); ReplayGain/R128 and the peak limiter apply as output gain.

## 1.1.0 — 2026-09-18

Playback and listening tools.

### Added
- ReplayGain / R128 with track or album gain and an optional peak limiter
- True gapless playback
- Crossfade (1–12 s). Requires gapless; turning gapless off turns crossfade off
- Synced lyrics on now playing from Navidrome/OpenSubsonic (`getLyricsBySongId`, then `getLyrics` / LRC)
- 10-band software equalizer with on/off, general presets, and AutoEq-style headphone targets
- Pause when headphones or Bluetooth audio disconnect
- Settings grouped into Account, Appearance, Playback, Equalizer, and About

### Changed
- Seek bar and player colors still follow the current album art

## 1.0.1 — 2026-09-18

### Fixed
- In-app player could not connect after 1.0.0, so login worked but nothing played. The session now accepts the app’s own controller and still rejects untrusted apps injecting a queue.

## 1.0.0 — 2026-09-18

First public release.

- Navidrome / Subsonic / OpenSubsonic client
- Playlist-first home, favorites, artist grid, search
- Album-art color wash and waveform seek bar
- Heart favorites (`star` / `unstar`)
- Token auth, Keystore-encrypted credentials, HTTPS with LAN-only HTTP
