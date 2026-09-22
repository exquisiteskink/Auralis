# Poweramp EQ DVC — Auralis sticky audio session

**Status:** Option A + mute-on-every-transition (extends 1.3.2 / #26).
**Constraint:** EQ / ReplayGain stay on platform `DynamicsProcessing` (not ExoPlayer `AudioSink`). AutoEq untouched.

## What DVC is

**DVC = Direct Volume Control** (Poweramp naming). With DVC on, listening level is dominated by Poweramp EQ’s attachment to the player’s **audio session**. If audio is audible while the EQ is unbound or mid-rebind, the stream plays near **full-scale** → the ~0.5–1s blast.

- KB: https://forum.powerampapp.com/kb/en_us/guides/using-direct-volume-control-dvc-r42/
- maxmpz: reuse **one** session for the whole player lifetime — https://forum.powerampapp.com/topic/21624-poweramp-equalizer-stops-processing-audio-after-some-time/

## Contract Auralis implements

1. `AudioManager.generateAudioSessionId()` once in `PlaybackService.onCreate` (not deferred to first prepare).
2. `ExoPlayer.setAudioSessionId(sticky)` on **every** player (primary + crossfade) **before** `prepare()`.
3. Broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` on that id **before any audible output**.
4. Broadcast `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` **only** in `onDestroy` (never on skip / promote / track change).
5. Dual-player crossfade shares the sticky id (true overlap; no second session).
6. **Mute-until-bound on every media transition** (not only cold start):
   - Triggers: cold start, `onMediaItemTransition` (skip / auto / seek-to-item / playlist), `TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED` (album change / `setMediaItems`), resume-mismatch, unexpected session id change.
   - Sequence: `volume = 0` immediately → re-assert sticky + OPEN → wait `STATE_READY` → settle **250 ms** → ramp via ReplayGain path (`exo.volume` → 1f when RG is on DynamicsProcessing).
   - Crossfade: next stays at 0 until READY + 250 ms settle before the overlap ramp starts.
   - `cancelCrossfade` must **not** restore volume while a mute-until-bound is in flight (album-change race).

Logcat filter: `Auralis/DvcSession`.

## Why #26 alone was not enough

Sticky session + CLOSE-only-on-destroy stops CLOSE/OPEN churn, but OEM **AudioTrack recreate** on album / format change can still leave Poweramp EQ briefly unbound while ExoPlayer volume is already 1f (`onMediaItemTransition` → `applyReplayGain`). That is the remaining blast on song/album change. Mute-until-READY+settle closes that window.

## User tips

- Prefer built-in EQ **or** Poweramp EQ insert chains, not both competing.
- Do not enable DVC in both Poweramp *player* and Poweramp *Equalizer*.
- For BT DVC testing, Absolute Volume off is often required.

## History

| PR | What it fixed | Gap |
|----|---------------|-----|
| #10 | RG via DynamicsProcessing; exo.volume≈1f; OPEN/CLOSE | Dual-session on crossfade; CLOSE-then-OPEN window |
| #23 | Share primary session on CF; promote order; OPEN-before-CLOSE | No service-lifetime sticky; skip without CF still churned; no cold-start mute |
| #26 sticky Option A | Lifetime sticky + CLOSE on destroy + mute-until-bound (cold start only) | Album change / skip still unmuted during AudioTrack recreate |
| **Mute every transition** | Mute→READY→250ms settle→ramp on skip / album / setMediaItems / CF ready | Extends #26; still not Option B (single-player when PA EQ) |

## Residual risk

- OEM / PA that need **>250 ms** after READY to bind — brief silence extends, or rare late spike if PA is slower.
- Competing built-in EQ bands + Poweramp on the same session.
- BT Absolute Volume / dual-DVC (PA player + PA EQ).
- If dual-player CF still spikes after READY+settle on a given OEM, next step is Option B (gate CF when external EQ active).
