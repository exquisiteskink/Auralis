# Auralis 1.3.1

This patch release improves recovery and playback reliability:

- Transient session restoration failures can be retried without restarting the app.
- Offline downloads validate against the response length when available, avoiding false failures from stale server metadata.
- Album pages safely handle duplicate track IDs.
- Sleep timers remain monotonic during one boot, survive reboots, and stay synchronized with Settings.
- Crossfade now truly overlaps playback and hands EQ to the promoted player without double-applying processing.
- Queues, playback position, shuffle, and repeat restore after process death, including notification, Bluetooth, and Android Auto resumption.

Validation: 48 unit tests passed, lint passed with 0 errors and 18 warnings, and the release APK build succeeded. Device and Android Auto head-unit testing was not performed.
