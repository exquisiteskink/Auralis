# Poweramp EQ DVC — sticky session + Option B + seek/pause mute

**Status:** Option A (sticky lifetime session) + mute-on-every-transition (#26 / #31) + **Option B** (gate dual-ExoPlayer crossfade when external EQ risk) + **seek/pause pre-mute** (`DvcGuardedPlayer`, pause-hold, longer settle).
**Claim:** Blast paths below are closed by **architecture + code**, not by owner QA.
**Constraint:** EQ / ReplayGain stay on platform `DynamicsProcessing` (not ExoPlayer `AudioSink`). AutoEq untouched. Playback error recovery (#30) untouched except mute-around retry seek (same player / sticky id).

## What DVC is

**DVC = Direct Volume Control** (Poweramp naming). With DVC on, listening level is dominated by Poweramp EQ’s attachment to the player’s **audio session**. If audio is audible while the EQ is unbound or mid-rebind, the stream plays near **full-scale** → the ~0.5–1s blast.

Critical Media3 fact: `DefaultAudioSink.flush()` **releases** the `AudioTrack` on every seek (Media3 1.5.x). Sticky session id alone does not prevent a unbound-feeling window across that recreate.

- KB: https://forum.powerampapp.com/kb/en_us/guides/using-direct-volume-control-dvc-r42/
- maxmpz: reuse **one** session for the whole player lifetime
- Research: `docs/DVC-RESEARCH-3.md`

## Architecture (what is now impossible)

| Failure mode | Why impossible |
|--------------|----------------|
| CLOSE/OPEN on every skip minting a new session | Sticky `generateAudioSessionId` for service life; CLOSE **only** in `onDestroy` |
| Dual-ExoPlayer crossfade creating a second session under PA EQ | Option B **does not create** the fade player when PA EQ (or known external EQ) is installed |
| Dual-AudioTrack promote teardown unbound window under PA EQ | Option B: no second player / no promote path when `ExternalEqRisk` |
| `setMediaItems` / album / skip unmuted while OEM AudioTrack recreates | Mute-until-bound on transition / `PLAYLIST_CHANGED`; READY + settle then ramp |
| **In-track seek / scrub at volume=1f across AudioTrack release** | `DvcGuardedPlayer` mutes **before** `seekTo*` (playback-thread order: volume=0 then flush); discontinuity belt; 450 ms settle when external EQ |
| **Pause / resume full-scale window** | Mute-**hold** on pause (no settle→unmute while paused); resume re-arms mute-until-bound; audio-focus pause covered via listener |
| Hard snap `volume=1f` from muted/low after settle | `applyReplayGain` ramps when volume &lt; 0.5; blocked while `sessionBindPending` / pause-hold |
| CF cancel snapping volume during mute | `restoreVolume=false` / pending guards |

## Contract Auralis implements

1. `AudioManager.generateAudioSessionId()` once in `PlaybackService.onCreate`.
2. `ExoPlayer.setAudioSessionId(sticky)` on every player **before** `prepare()`.
3. Broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` before audible output; CLOSE only on destroy.
4. **Option B gate** (`ExternalEqRisk`): if Poweramp Equalizer (`com.maxmpz.equalizer`) or known Wavelet ids installed, **dual-ExoPlayer crossfade does not run**.
5. **Mute-until-bound** on media transitions + **pre-mute** on seek/pause via `DvcGuardedPlayer`.
6. **Pause hold:** while paused, volume stays 0; settle cannot unmute until play/resume.
7. Settle: **250 ms** default; **450 ms** when external EQ risk (seek recreate bind window).
8. Detection honesty: no public “DVC is on” API — package presence ⇒ fail closed for dual CF.

Logcat filter: `Auralis/DvcSession`.

## Detection (`ExternalEqRisk`)

- Primary: `com.maxmpz.equalizer` (Poweramp Equalizer).
- Also gated: Wavelet package ids listed in code.
- PackageManager errors ⇒ fail closed (treat as unsafe).
- Unknown third-party EQs not in the list are **residual** risk for dual CF only.

## Residual (honest)

- OEM / PA needing **&gt;450 ms** after seek recreate — rare late spike; silence may need longer.
- External EQ packages **not** in `KNOWN_EXTERNAL_EQ_PACKAGES` — dual CF may still run.
- Competing built-in EQ bands + Poweramp on the same session.
- BT Absolute Volume / dual-DVC (PA player + PA EQ).
- Media3 still releases AudioTrack on seek — we mute across that window rather than fighting OEM flush bugs with keep-on-seek.
- Unmuted scrub under DVC would require a non-Exo engine (see `docs/OPTIONS-DVC-PLAYER.md`) — out of scope; no-blast preferred.

## User tips

- Prefer built-in EQ **or** Poweramp EQ insert chains, not both.
- Do not enable DVC in both Poweramp *player* and Poweramp *Equalizer*.
- For BT DVC testing, Absolute Volume off is often required.
- With Poweramp EQ installed, Crossfade in Settings is informationally gated: overlap is disabled; gapless still works.
- Expect a short mute on seek / pause-resume when PA EQ is installed — that silence is the blast shield.

## History

| PR | What it fixed | Gap |
|----|---------------|-----|
| #10 | RG via DynamicsProcessing; exo.volume≈1f; OPEN/CLOSE | Dual-session on crossfade; CLOSE-then-OPEN window |
| #23 | Share primary session on CF; promote order; OPEN-before-CLOSE | No service-lifetime sticky; skip without CF still churned |
| #26 sticky Option A | Lifetime sticky + CLOSE on destroy + mute-until-bound (cold start) | Album / skip unmuted during AudioTrack recreate |
| #31 mute + Option B | Mute on every transition; gate dual CF under PA EQ | **Seek / pause** not pre-muted; listener raced flush |
| **This change** | `DvcGuardedPlayer` mute-before seek/pause; pause-hold; 450 ms settle under external EQ; error-retry seek mute | See residual above |
