# Changelog

## Unreleased

### Added
- Sleep timer in Settings → Playback (15 / 30 / 45 / 60 minutes or end of track). Seek, a new queue, or resume after pause clears it.

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
