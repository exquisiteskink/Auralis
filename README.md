# Auralis

Auralis is an audiophile-focused Android player for **Navidrome**, **Subsonic**, and **OpenSubsonic** servers.

This repository currently ships a **test APK** for feedback. There is no GitHub Release.

## Highlights

- Home is playlists first, then recently played albums, then recently added albums
- Bottom navigation: Home, Artists, Search, Settings
- Artists in a square Plexamp-style grid
- Artist page: hero image, top 5 popular songs, horizontally scrolling albums, collapsible About with similar artists that exist in your library
- Popular songs ranked up to 20 (Subsonic `getTopSongs`, with a Deezer fallback matched to library tracks)
- Album page with cover art and tracks in disc/track order
- Mini player while something is playing; swipe up to the now-playing screen, swipe up again for the queue, swipe down to go back, swipe down again to collapse
- Background gradient follows album-art colors in light and dark mode
- Glassy, semi-transparent surfaces
- Original-file streaming by default (no transcode)

## Security

Auralis does not send plaintext passwords.

- Default login is Subsonic token auth: `t = md5(password + salt)` with a fresh salt per API call ([OpenSubsonic authentication](https://opensubsonic.netlify.app/docs/api-reference))
- If the server returns error `41` (token auth not supported, typically LDAP), it falls back to the documented `p=enc:` hex encoding — never a raw `p=` password
- OpenSubsonic API keys are supported (`apiKey`, without `u`)
- Credentials are AES-256-GCM encrypted with a key in Android Keystore
- Auto-backup excludes the credential store
- Stream and cover URLs use a session salt, not a reusable plaintext password
- HTTP is allowed for LAN servers; the login screen warns when you use it. Prefer HTTPS

Passwords are never shown again after sign-in.

## Test APK

A debug-signed test APK is produced by:

```bash
export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-17.0.20+8"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug
```

The APK lands at:

`app/build/outputs/apk/debug/app-debug.apk`

Copy it to your phone and install it (enable “Install unknown apps” for the file manager you use). Uninstall a previous Auralis test build first only if the signing key changed; this repo’s `keystore/debug.jks` keeps the test signature stable.

This keystore is **debug-only**. It is not a release key.

## Requirements

- Android 8.0+ (API 26)
- A Navidrome, Subsonic, or OpenSubsonic server
- JDK 17 and Android SDK 35 to build from source

## Popular songs & artist bios

1. Server `getTopSongs` / `getArtistInfo2` when the server has Last.fm (or similar) configured
2. Otherwise Deezer’s public API for ranked titles, matched against songs already in your library
3. MusicBrainz + Wikipedia for a biography if the server does not provide one

Similar artists only include people who already exist in your library. Tapping one opens that artist.

## License

MIT
