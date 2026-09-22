# Auralis

Auralis is an Android music player for **Navidrome**, **Subsonic**, and **OpenSubsonic** servers. It is built for people who keep their own library: original-file streaming, a player that follows the album art, and a layout that stays out of the way of the music.

**Version 1.3.1** is the current public release. See [CHANGELOG.md](CHANGELOG.md).

## Install

1. Download `Auralis-1.3.1.apk` from the [latest GitHub Release](https://github.com/exquisiteskink/Auralis/releases/latest).
2. On your phone, allow installing from the app you use to open the file.
3. Open the APK and install.

Android 8.0 (API 26) or later is required. A reachable Navidrome, Subsonic, or OpenSubsonic server is needed to sign in and download music; saved tracks can then play offline.

You can install 1.3.1 over an earlier version when both were signed with the same key.

## What it does

- **Home** — larger playlist cards first, then Continue, recently played and recently added albums, compact favorite tracks, artists in rotation, and genre shuffle chips.
- **Artists** — four-column square grid, with a list view if you prefer.
- **Artist page** — circular photo, play, albums, popular tracks, biography from your server, and similar artists that already exist in the library.
- **Album and playlist pages** — cover, play/shuffle, numbered track list, and labeled sections for multi-disc albums. Albums also show the year and record label when supplied by your server.
- **Search** — artists, albums, songs, and genres. Before you start searching, browse genre chips and recently played artists; focusing the search field switches to search results.
- **Now playing** — full-screen player with larger cover art, a close button at the top, and the seek bar beneath the artwork, and track details and larger playback controls grouped closely below it. Includes album-art color wash, waveform seek bar, codec / bit depth / sample rate, heart favorites, synced lyrics, and a swipe-up queue. Artwork and sheet transitions are softened. Tap the cover or the lyrics icon. Collapse it to a mini player above the tab bar.
- **Playback** — Media3 ExoPlayer, original streams by default, optional transcode, ReplayGain/R128 with peak limiting, true gapless, optional crossfade, 10-band EQ with general presets, pause on headphone/Bluetooth disconnect, lock-screen and notification controls. ReplayGain uses the platform audio-processing path when available for more stable external-EQ DVC volume during track skips.

- **Offline downloads** — download albums or playlists, then open Home → Downloads to play saved tracks, including after restarting offline. Settings includes Wi-Fi-only downloads and storage clearing.
- **Sleep timer** — Settings → Playback offers 15 / 30 / 45 / 60 minutes or the end of the current track.
- **Android Auto** — browse playlists, recently played albums, favorites, and recently added albums. Voice search is not included; head-unit validation is still pending.

Colors on the player and throughout the app are sampled from the current cover (dark and light). The seek bar uses that highlight color.

## Sign in

Open Auralis and enter:

- Server URL, for example `https://music.example.com`
- Username and password, **or** an OpenSubsonic API key

Auralis talks to the OpenSubsonic REST API (`v=1.16.1`). Default auth is a salted token (`t = md5(password + salt)`). If the server returns error 41 (typical for LDAP), it falls back to hex-encoded password **only over HTTPS**. Prefer HTTPS.

## Security

- Passwords are encrypted with AES-256-GCM; the key stays in Android Keystore and is never shown again after sign-in.
- Auto-backup excludes the credential store.
- HTTP is allowed only for LAN hosts (localhost, `.local` / `.lan`, or a private IP). Public servers must use HTTPS.
- Redirects must keep the same scheme, hostname, and port. Cross-protocol redirects are blocked.
- TLS uses the system certificate store. Self-signed public certificates are not trusted.
- Popular tracks and biographies come from *your* server. Auralis does not send artist names to Deezer, MusicBrainz, or Wikipedia.

See [REVIEW.md](REVIEW.md) for the security and correctness review behind this release.

## Build from source

JDK 17 and Android SDK 35:

```bash
export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-17.0.20+8"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

A local, git-ignored `keystore/debug.jks` keeps the debug signature stable on the machine that produced this release. Fresh clones fall back to Android’s generated debug key.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## License

MIT. See [LICENSE](LICENSE).
