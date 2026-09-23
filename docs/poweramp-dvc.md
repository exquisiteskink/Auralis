# Poweramp EQ DVC — Auralis sticky session + Option B

**Status:** Option A (sticky lifetime session) + mute-on-every-transition (#26 / #31) + **Option B** (gate dual-ExoPlayer crossfade when external EQ risk).
**Claim:** Blast paths below are closed by **architecture + code**, not by owner QA.
**Constraint:** EQ / ReplayGain stay on platform `DynamicsProcessing` (not ExoPlayer `AudioSink`). AutoEq untouched. Playback error recovery (#30) untouched.

## What DVC is

**DVC = Direct Volume Control** (Poweramp naming). With DVC on, listening level is dominated by Poweramp EQ’s attachment to the player’s **audio session**. If audio is audible while the EQ is unbound or mid-rebind, the stream plays near **full-scale** → the ~0.5–1s blast.

- KB: https://forum.powerampapp.com/kb/en_us/guides/using-direct-volume-control-dvc-r42/
- maxmpz: reuse **one** session for the whole player lifetime — https://forum.powerampapp.com/topic/21624-poweramp-equalizer-stops-processing-audio-after-some-time/

## Architecture (what is now impossible)

| Failure mode | Why impossible |
|--------------|----------------|
| CLOSE/OPEN on every skip minting a new session | Sticky `generateAudioSessionId` for service life; CLOSE **only** in `onDestroy` |
| Dual-ExoPlayer crossfade creating a second session | Both players always get sticky id **and** Option B **does not create** the fade player when PA EQ (or known external EQ) is installed |
| Dual-AudioTrack promote teardown unbound window under PA EQ | Option B: no second player / no promote path when `ExternalEqRisk` is true — one player owns output |
| `setMediaItems` / album change → `cancelCrossfade` → `applyReplayGain` → `volume=1f` while OEM AudioTrack recreates unbound | Mute-until-bound on `PLAYLIST_CHANGED` **before** cancel; `cancelCrossfade(restoreVolume=false)`; READY + 250 ms settle then ramp |
| Skip / auto / seek-to-item transition unmuted while unbound | Mute on every `onMediaItemTransition` (non-fading) before RG restore |
| Hard snap `volume=1f` from muted/low after settle | `applyReplayGain` ramps when volume &lt; 0.5; never applies while `sessionBindPending` |
| CF cancel snapping volume during playlist mute | `restoreVolume=false` / `!sessionBindPending` guards |

## Contract Auralis implements

1. `AudioManager.generateAudioSessionId()` once in `PlaybackService.onCreate`.
2. `ExoPlayer.setAudioSessionId(sticky)` on every player **before** `prepare()`.
3. Broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` before audible output; CLOSE only on destroy.
4. **Option B gate** (`ExternalEqRisk`): if Poweramp Equalizer (`com.maxmpz.equalizer`) or another known session-insert EQ package is installed, **dual-ExoPlayer crossfade does not run**. Gapless / Media3 single-player transitions remain. Setting may stay “on”; runtime is single-player.
5. **Mute-until-bound** on every media transition (safety belt even with one player):
   - Triggers: cold start, `onMediaItemTransition`, `PLAYLIST_CHANGED`, resume-mismatch, unexpected session id change, and CF-promote only if dual CF somehow ran under external-EQ risk.
   - Sequence: `volume = 0` → re-assert sticky + OPEN → `STATE_READY` → settle **250 ms** → ramp via ReplayGain path.
6. Detection honesty: there is **no** public “DVC is on” API. Package presence ⇒ fail closed (disable dual CF). Missing package ⇒ dual CF allowed (built-in path).

Logcat filter: `Auralis/DvcSession`.

## Detection (`ExternalEqRisk`)

- Primary: `com.maxmpz.equalizer` (Poweramp Equalizer).
- Also gated: Wavelet package ids listed in code.
- PackageManager errors ⇒ fail closed (treat as unsafe).
- Unknown third-party EQs not in the list are **residual Option B-plus** risk (see below).

## Residual (Option B-plus — honest)

- OEM / PA needing **&gt;250 ms** after READY on **single-player** AudioTrack recreate — brief silence may need longer settle; rare late spike if PA is slower than settle.
- External EQ packages **not** in `KNOWN_EXTERNAL_EQ_PACKAGES` — dual CF may still run.
- Competing built-in EQ bands + Poweramp on the same session.
- BT Absolute Volume / dual-DVC (PA player + PA EQ).

## User tips

- Prefer built-in EQ **or** Poweramp EQ insert chains, not both.
- Do not enable DVC in both Poweramp *player* and Poweramp *Equalizer*.
- For BT DVC testing, Absolute Volume off is often required.
- With Poweramp EQ installed, Crossfade in Settings is informationally gated: overlap is disabled; gapless still works.

## History

| PR | What it fixed | Gap |
|----|---------------|-----|
| #10 | RG via DynamicsProcessing; exo.volume≈1f; OPEN/CLOSE | Dual-session on crossfade; CLOSE-then-OPEN window |
| #23 | Share primary session on CF; promote order; OPEN-before-CLOSE | No service-lifetime sticky; skip without CF still churned; no cold-start mute |
| #26 sticky Option A | Lifetime sticky + CLOSE on destroy + mute-until-bound (cold start only) | Album change / skip still unmuted during AudioTrack recreate |
| #31 mute every transition | Mute→READY→250ms settle→ramp on skip / album / setMediaItems / CF ready | Dual-player CF second AudioTrack / promote teardown still possible under PA |
| **This change (Option B on #31)** | Gate/drop dual-ExoPlayer CF when external EQ risk; keep sticky + mute belt; harden low→1f ramp | See residual above |
