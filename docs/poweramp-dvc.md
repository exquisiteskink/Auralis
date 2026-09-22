# Poweramp EQ DVC — Auralis sticky audio session

**Status:** Option A implemented in 1.3.2.
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
6. **Mute-until-bound:** cold start, resume-mismatch, and unexpected `onAudioSessionIdChanged` → volume 0 → OPEN → ~80 ms settle → ramp. Primary skip/CF path stays on sticky id with no CLOSE/OPEN.

Logcat filter: `Auralis/DvcSession`.

## User tips

- Prefer built-in EQ **or** Poweramp EQ insert chains, not both competing.
- Do not enable DVC in both Poweramp *player* and Poweramp *Equalizer*.
- For BT DVC testing, Absolute Volume off is often required.

## History

| PR | What it fixed | Gap |
|----|---------------|-----|
| #10 | RG via DynamicsProcessing; exo.volume≈1f; OPEN/CLOSE | Dual-session on crossfade; CLOSE-then-OPEN window |
| #23 | Share primary session on CF; promote order; OPEN-before-CLOSE | No service-lifetime sticky; skip without CF still churned; no cold-start mute |
| **Sticky Option A** | Lifetime sticky + CLOSE on destroy + mute-until-bound (incl. cold start) | Supersedes #23 |
