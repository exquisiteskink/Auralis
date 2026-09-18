# Auralis review — 2026-09-18

Reviewed the working tree, including its existing uncommitted changes. Those changes and the staged removal of the debug keystore were preserved. This is a source review with local regression tests, not a claim that all possible bugs have been eliminated.

## Security fixes

| Finding | Impact and fix |
| --- | --- |
| Global trust-all TLS switch | Could disable certificate and hostname checks for every request. Removed the switch and custom TLS implementation; use OkHttp's verified TLS defaults. Self-signed/untrusted certificates now fail. |
| LAN check accepted any name beginning with `fc`/`fd` | Public DNS names could qualify for plaintext authentication. Parse IPv6 literals, validate LAN DNS results, and enforce the connected address for HTTP requests. |
| Redirect origin omitted the port | Authenticated URLs could be redirected to another service on the same host. Require scheme, hostname, and port to match; reject embedded credentials and limit redirect hops. |
| Unvalidated base URL credentials/query/fragment | Could mix unexpected authentication parameters into every API request. Reject these components, retaining support for server path prefixes. Revalidate stream/artwork URLs too. |
| Password fallback not guarded on every request | Restored/injected hex-password credentials could generate HTTP URLs. Require HTTPS whenever hex-password authentication is used. Hex encoding is reversible, not encryption. |
| Artwork URLs in disk cache | API keys or hex passwords may appear in image cache metadata. Disable artwork disk caching. Server-provided artwork must also be HTTP(S), not file/content URIs. |
| Unbounded API response bodies | A large/chunked response could exhaust memory. Limit decoded response bodies to 16 MiB and API calls to 60 seconds; cancellation cancels the underlying request. |
| Media-controller access | Explicitly reject untrusted controllers; trusted external controllers retain playback controls but cannot replace the queue with arbitrary media URIs. |
| Release used shared debug signing | Remove release signing with the public test key. Release output is unsigned until private signing is supplied. Fresh clones can build debug with Android's generated key if the local test keystore is absent. |

## Correctness fixes

- Publish login credentials only after authentication succeeds, including password fallback. Serialize startup/manual login; avoid relogging an active session on activity recreation. Sign-out cancels outstanding calls, resets playback, and discards saved navigation state.
- Remove metadata caches that leaked stale artist/song results between servers/accounts and became stale after listening. Do not treat two missing artist IDs as a match.
- Preserve coroutine cancellation in optional lookups and screen loaders, preventing cancelled screens/searches from continuing work.
- Update the playback queue before synchronous player callbacks, deduplicate pending controller connections, retain queued operations during connection, release controllers on logout, and handle disconnection.
- Respect favorite overrides, prevent overlapping updates for one song, and restore the original favorite state if the server rejects the change.
- Cancel obsolete artwork work, limit decoding size, and prevent old artwork from replacing the current song's palette.
- Show actual shuffle traversal in Up Next and use the corresponding queue index when a row is tapped. Bound seek positions; count listening time for scrobbling rather than a seeked-to position. Surface playback errors and allow retry from idle.
- Send `format=raw` for Original quality. `maxBitRate=0` alone only removes the bitrate cap. Preserve playback intent while changing quality and stop overwriting that setting on recomposition.
- Avoid double URI decoding of artist names. Key detail-screen state by the requested ID. Make popular tracks scrollable and album/playlist rows lazy, including playlists with duplicate songs.
- Fix JSON null string handling, apply Scaffold insets, and move display-cutout styling into API-qualified resources. Use Media3's Android annotation opt-in rather than relying on the ineffective Kotlin compiler flag.

## Validation

Run with JDK 17 and Android SDK 35:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The regression suite covers LAN classification, malformed base URLs, cross-port credential redirects, same-origin redirects/loops, invalid TLS certificates and hostnames, token/API-key authentication, failed password fallback, cancellation, oversized chunked bodies, original/transcoded parameters, metadata isolation, JSON nulls, shuffled duplicate queue entries, and favorite overrides.

Verified result: **17 tests passed; lint passed with 0 errors and 28 warnings; debug and unsigned-release APK builds succeeded.** The debug APK's v2 signature was verified with Android `apksigner`. `git diff --check` passed.

Artifacts: `app/build/outputs/apk/debug/app-debug.apk` (installable test build) and `app/build/outputs/apk/release/app-release-unsigned.apk` (requires private release signing).

On 2026-09-18, OSV's batch endpoint returned no matching advisories for 135 resolved Maven runtime dependency entries (including BOM/constraint entries). This does not cover unpublished vulnerabilities, device codecs, or the music server. Existing dependency-update, resource, and intentional LAN cleartext lint warnings are separate from vulnerabilities.

## Device checks and compatibility notes

No instrumented device/emulator tests were run. Verify login/relaunch/rotation, logout while requests are active, large playlists, rapid next/favorite actions, shuffled queues, artwork failures, quality changes while buffering, headset disconnect, background playback, and notification/lock-screen controls against a test music server.

HTTPS servers need a valid trusted certificate. Cross-origin redirect deployments must use their final server URL directly. LAN HTTP remains supported and is still unencrypted. Existing installed builds may have old artwork cache entries; clear the application's cache to remove those older entries. The new build does not write new artwork entries. Debug builds are for testing; distribute only privately signed release builds.

References: [OpenSubsonic stream parameters](https://opensubsonic.netlify.app/docs/endpoints/stream/), [Android media session controls](https://developer.android.com/media/media3/session/control-playback), [OSV API](https://google.github.io/osv.dev/post-v1-query/).
