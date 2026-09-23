# DVC Research 3 — Seek / Pause / Track-change blast (2026-09-22 ET)

**Repo tip audited:** Auralis `main` @ 51911fb (v1.3.3) — sticky session (#26) + mute-on-transition + Option B (#31) **already merged**.
**Owner report:** Large volume spikes remain on **seeking, pausing, changing tracks** with Poweramp EQ + DVC on.

---

## 1. Poweramp EQ DVC — deeper mechanics

### What DVC does
- Poweramp “Direct Volume Control” takes a more direct path to hardware volume / output, giving EQ more headroom.
- With DVC **on**, listening level is dominated by PA EQ’s insert on the player’s **audio session**. If PCM is audible while PA is unbound or mid-rebind → near **full-scale** (~0.5–1s blast).
- KB: https://forum.powerampapp.com/kb/en_us/guides/using-direct-volume-control-dvc-r42/
- maxmpz guidance: **one session id for whole player lifetime**; do not churn OPEN/CLOSE per track; for crossfade prefer one session / Multi Session — https://forum.powerampapp.com/topic/21624-poweramp-equalizer-stops-processing-audio-after-some-time/
- No public “is DVC on?” API. Client contract = Android MusicFX OPEN/CLOSE + sticky session.

### Binding timing
1. Player broadcasts `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` with session id.
2. PA Equalizer creates insert effects on that session (async; tens–hundreds of ms).
3. **AudioTrack lifecycle ≠ session lifecycle.** Effects attach to the *session*, but OEM/PA paths often glitch when the underlying `AudioTrack` is released and a new one is created on the **same** session id (refcounting / flush bugs). Sticky session alone does **not** prevent unbound-feeling windows across track recreate.
4. Absolute Volume (BT) + dual DVC (PA player + PA EQ) are known conflict classes — disable Absolute Volume for BT DVC testing.

### App-side actions that drop attenuation / emit full-scale

| App action | Platform / Exo effect | PA DVC risk |
|------------|----------------------|-------------|
| **seekTo / scrub** | Media3 `DefaultAudioSink.flush()` **always releases** `AudioTrack`/`AudioOutput` then recreates on next buffer (Media3 1.5.1; comment cites b/7941810). Session id may stay sticky. | **HIGH** — new track plays at `player.volume` (usually 1f) before PA rebinds |
| **pause** | Normally `AudioTrack.pause()` only; some OEMs later release; PA may flush DSP buffers | **MEDIUM–HIGH** — remnant buffer / resume recreate |
| **play / resume** | May re-init track after pause release | **HIGH** if unmuted before PA settle |
| **setMediaItem / setMediaItems / skip** | Decoder + sink flush / configure → track recreate | **HIGH** (partially covered by #31 mute-on-transition) |
| **prepare** | Creates track when buffers arrive | Cold-start covered by mute-until-bound |
| **setVolume(1f)** while unbound | Instant full-scale into DVC path | Catastrophic if PA unbound |
| **stop / release** | Track teardown | CLOSE only on destroy is correct |
| **playWhenReady flip** | Focus / pause / play path | Resume mismatch only was guarded |

### ExoPlayer / Media3 facts (1.5.1 used by Auralis)
- `MediaCodecAudioRenderer.onPositionReset` → `audioSink.flush()`.
- `DefaultAudioSink.flush()` (current Media3): **releases audio output every flush** — experimental “keep AudioTrack on seek” was removed; TODO remains “experiment with not releasing”.
- Therefore: **in-track seek always recreates AudioTrack** under Exo 1.5.x, even with sticky `audioSessionId`.
- Volume ramp inside sink after recreate is ~20 ms PCM ramp — far too short for PA bind.

### Peer apps (honest)
| App | Pattern | With PA DVC |
|-----|---------|-------------|
| **Poweramp player** | Owns DVC internally (OpenSL/AAudio path) | N/A as external client |
| **Symfonium** | Exo-based; disables dual complexity; documents OEM EQ flush bugs (spike = remnant at full volume); author reports DynamicsProcessing fragility | Works for many; still OEM-dependent; recommend off built-in DSP when using PA |
| **Spotify + PA EQ** | Chronic skip/first-play spikes on Android 14+; users disable DVC or use PA per-player fade | Confirms blast class is environmental + session/track lifecycle |
| **Metrolist / single-Exo Media3** | One player, OPEN while playing | No dual-session; still subject to seek flush recreate |
| **Plexamp** | Closed; not documented as PA-DVC-tuned | Do not assume they solve DVC |

**Honest path that works WITH PA DVC:** sticky session + never audible while unbound + **mute before any action that flushes/recreates AudioTrack** (seek, pause/resume, media replace) + no second player under PA. Fancy crossfade is secondary.

---

## 2. Main-branch audit — what #31 still missed

Covered after #26/#31:
- Sticky lifetime session; CLOSE only destroy
- Mute on `onMediaItemTransition` / `PLAYLIST_CHANGED`
- Option B: no dual-Exo CF when `com.maxmpz.equalizer` installed
- `applyReplayGain` blocked while `sessionBindPending`; low→1f ramp

**Still open (matches owner report):**

1. **In-track seek** — `onPositionDiscontinuity(SEEK)` only `cancelCrossfade`; **never mutes**. MediaSession/`seekTo` hits Exo directly → flush → new AudioTrack at volume 1f → blast.
2. **Pause / resume** — user pause cancels CF with `restoreVolume=true` possible; **no mute-before-pause**; resume only mutes if `audioSessionId != sticky` (rare). Pause remnant + resume recreate unbound.
3. **Race** — Listener mutes *after* Exo queues seek on playback thread. Need mute **before** `seekTo`/`pause` on the same main-thread call order so player messages stay ordered (volume=0 then flush).
4. **Settle 250 ms** — may be short for PA after seek recreate; Option B residual already admitted this.
5. **Error-retry `seekTo`** (#30 path) — can seek without mute (should mute without rebuilding player / sticky id).

Not wrong: Option B gate, sticky id, no EQ in AudioSink, #30 recovery structure.

---

## 3. Player alternatives (brief)

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **A. Stay Exo + harden** (mute-before seek/pause; hold mute while paused; longer settle under ExternalEqRisk; keep Option B) | Preserves gapless, Auto, offline, formats; closes blast by architecture | Brief mute on seek/pause; CF stays gated under PA | **Ship now** |
| **B. Custom AudioSink “keep track on flush”** | Could avoid recreate | Media3 removed keep-on-seek for OEM bugs; high regression risk | Defer |
| **C. MediaPlayer / MediaCodec raw** | Simpler track lifecycle sometimes | Lose Media3 session/Auto/gapless/CF; still seek flush issues on many OEMs | Poor fit |
| **D. Fork / rewrite to AAudio|OpenSL like Poweramp** | Could own DVC like PA | Months; Auto/offline/gapless/CF rewrite; product tradeoffs large | Only multi-step epic |

**Recommendation:** **A now**. Exo seek *is* fundamentally “flush = release track” in Media3 1.5 — incompatible with unmuted PA DVC during seek — but **compatible if we never unmute across that window**. Full player rewrite not required to close blasts; only if we later need unmuted scrub under DVC (unlikely requirement).

---

## 4. Implementation shipped in this pass
- `DvcGuardedPlayer` (ForwardingPlayer): mute **before** every seek*/pause/stop; on play/resume arm mute-until-bound
- Hold mute while paused (no settle→unmute under pause)
- Seek discontinuity + error-retry seek also mute
- Longer settle when ExternalEqRisk (400 ms)
- Option B + sticky + #30 untouched in spirit (retry still same player/session; only adds mute)

