# Existing API 36 x86_64 emulator

Observed 2026-10-20 for S03.2 qualification. Existing runner `emulator-5580` was
reused; no new emulator was created, rebooted, resized or reconfigured.

- Fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:user/release-keys`.
- Android 16 / API 36, x86_64, page size 4096 bytes.
- `wm size`: physical 1080x2400, pre-existing override 2880x1800.
- `wm density`: physical 420, pre-existing override 240 dpi.
- Font scale: 1.0. Captured appearance is light; no theme/density preference
  changes were made for the run.
- Current WebView: `com.google.android.webview` 143.0.7499.35.
- Native Activity tasks 2384/2385 were distinct; both observed full bounds
  `Rect(0, 0 - 2880, 1800)`, despite requested smaller launch bounds.
- Before install: `/data` 5.8 GiB total, 5.0 GiB used, 594 MiB available.
  Universal APK install failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE`.
  A 111 MiB x86_64-only qualification APK installed without deleting existing
  apps, workbooks or caches. Normal builds still include both supported ABIs.

This is emulator evidence for one profile, not physical GoogleBook, side-by-side
freeform placement, external display, hardware input, 16 KiB or API 37 acceptance.
