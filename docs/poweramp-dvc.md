# Poweramp Equalizer DVC playback guard

Poweramp describes Direct Volume Control (DVC) as a more direct output volume path with more EQ headroom. Its guidance also warns about Bluetooth Absolute Volume and enabling DVC in both Poweramp apps. See [Poweramp's DVC guide](https://forum.powerampapp.com/kb/en_us/guides/using-direct-volume-control-dvc-r42/).

Poweramp's developers recommend one audio session for the player lifetime. Auralis allocates a sticky session, opens it for external effects before playback, and closes it on service destruction. See the [Poweramp Equalizer discussion](https://forum.powerampapp.com/topic/21624-poweramp-equalizer-stops-processing-audio-after-some-time/#findComment-102916).

Media3 1.5.1 releases its AudioTrack when `DefaultAudioSink.flush()` runs. A seek can therefore create a new AudioTrack even though the audio session ID stays the same. Auralis mutes before controller seeks and queue replacement, keeps the mute while paused, and starts the settle timer after Media3 reports a new AudioTrack for a guarded seek. It then ramps player volume back up. On errors, Auralis stays muted through retry.

When Poweramp Equalizer or another known external EQ is installed, Auralis disables dual-player crossfade. It also uses `pauseAtEndOfMediaItems` and advances the queue while muted so an automatic track transition does not start the next track audibly before the guard runs. This introduces a short gap between songs. The setting may still show crossfade enabled, but overlap does not run in this mode.

There is no public API to read Poweramp Equalizer's active DVC setting or to learn when it has rebound to a new AudioTrack. Package detection is only a conservative proxy. The 450 ms wait is a heuristic, so this code cannot establish that every phone and output route is safe. A device test with Poweramp Equalizer DVC enabled is required before claiming the headphone spike is fixed. Test seeking, scrubbing, pause/resume, manual and automatic next, replacing an album queue, repeat, sleep timer, and Bluetooth routing at a low hardware volume first. Capture `Auralis/DvcSession` logs with any remaining spike.
