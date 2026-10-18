# Platform profile — HP Googlebook 14 (`quartz`)

**Observed:** 2026-10-18 via `adb -s C1L63708PZ shell getprop` and `dumpsys`.
**Role:** primary physical target for the current slice.

| Property | Observed value |
| --- | --- |
| Product / device | HP Googlebook 14, `quartz` |
| Manufacturer | HP |
| Build fingerprint | `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys` |
| Android release / API | Android 17 / API 37 (`ro.build.version.sdk=37`; minor property blank) |
| Security patch | 2026-09-01 |
| Build date (kernel string) | 2026-09-08 |
| Characteristics | `desktop` |
| ABI | `arm64-v8a` (`ro.product.cpu.abilist=arm64-v8a`) |
| Page size | 4096 bytes (`getconf PAGE_SIZE`) |
| RAM | 15,852,148 kB total (~15.1 GiB) |
| Internal display | 2880×1800, 260 dpi, 120 Hz mode reported |
| Window mode observed | Freeform desktop window, task type `standard`, bounds ~1900×1170 px in the test session |
| WebView provider | `com.google.android.webview` 149.0.7827.5, targetSdk 36 |
| Input — keyboard | Built-in keyboard device reported by `dumpsys input` (KeyboardType 1/2 entries) |
| Input — touchpad | `hid-over-i2c 06CB:96E7 Touchpad` (MOUSE + TOUCHPAD) |
| Input — mouse | `Logi M550 Mouse` (MOUSE) |
| Android user observed by app | user 10 |
| Free `/data` reported | ~365 GiB free at observation |

## Limits

- This is a **4 KiB-page** device. ELF artifacts are checked for 16 KiB load
  alignment, but runtime 16 KiB support is not established.
- The ChromeOS/Android desktop caption and task behavior are real; the app uses
  safe-drawing insets. No external display was attached during the recorded run.
- Physical keyboard/mouse/trackpad input was used for simple UI automation (via
  `adb shell input` for pointer coordinates), not as a formal input-boundary
  test. IME composition, TalkBack speech, trackpad gestures and hardware shortcut
  conflicts remain unqualified.
- Screen captures show the app alongside an unrelated Console window and other
  desktop applications; those applications are not part of Cloudy Data.
