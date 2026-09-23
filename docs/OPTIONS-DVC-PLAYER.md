# Options brief — PA DVC seek/pause blast (CoS)

**Date:** 2026-09-22 ET  
**Context:** After #26 sticky + #31 mute/Option B, owner still hears blasts on seek/pause/track change.

## Choice (owner-aligned: no-blast > fancy CF)

| # | Option | Tradeoffs | Decision |
|---|--------|-----------|----------|
| **1** | **Stay Exo; mute-before seek/pause; hold mute while paused; longer settle under ExternalEqRisk; keep Option B** | Brief silence on seek/pause/resume; CF remains gated when PA EQ installed | **IMPLEMENTING NOW** |
| 2 | Custom AudioSink keep-track-on-flush | Fights Media3 OEM workaround (flush always releases); device regressions | Defer |
| 3 | Switch to MediaPlayer / raw MediaCodec | Lose Media3 session, Auto browse, gapless polish; seek still flushes on many OEMs | Reject |
| 4 | Rewrite playback on AAudio/OpenSL (Poweramp-style) | Months; Auto/offline/gapless/CF all redesign | Epic only if (1) fails on device |

**Why not rewrite:** Media3 seek *does* always recreate AudioTrack, but that is compatible with PA DVC **if volume stays 0 across recreate+bind**. Architecture fix closes blasts without abandoning Exo.

**Interim shipped in same PR as (1):** DvcGuardedPlayer + pause hold + 450ms settle under external EQ + docs.

**If rewrite later:** open issue “Epic: non-Exo audio engine for DVC scrub without mute” — not blocking this PR.
