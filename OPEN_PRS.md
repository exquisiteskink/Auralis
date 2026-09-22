# Open PRs

Last updated: 2026-09-22 (America/New_York)

| PR | Branch | Status | Notes |
|----|--------|--------|-------|
| [#27](https://github.com/exquisiteskink/Auralis/pull/27) | `fix/login-gate-only-when-logged-out` | Open | Auth gate: splash while restoring saved creds; Login only when no valid session. Transient network keeps app shell. |
| [#28](https://github.com/exquisiteskink/Auralis/pull/28) | `feature/search-song-actions` | Open | Search song tap → bottom sheet: Play, Go to artist, Go to album (if `albumId`). |
| [#29](https://github.com/exquisiteskink/Auralis/pull/29) | `feature/search-suggestions` | Open | As-you-type suggestions via Subsonic/OpenSubsonic **`search3`** (limited counts) + client-side recent queries. No invented APIs. |

## Explicitly left alone
- **#26** sticky DVC / `PlaybackService` audioSessionId — merged on main; do not reopen or touch PlaybackService DVC in these PRs.
- No EQ/ReplayGain in ExoPlayer `AudioSink`.

## Merge note
`#28` and `#29` both touch `SearchScreen.kt` (independent of `#27`). Merge one, then rebase the other.
