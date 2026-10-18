# Platform profile — API 37 x86_64 emulator

**Observed:** 2026-10-18 via `adb -s emulator-5580 shell getprop`.
**Role:** second ABI and desktop-windowing development runner; not physical-hardware evidence.

| Property | Observed value |
| --- | --- |
| AVD / model | `sdk_gphone64_x86_64` |
| Build fingerprint | `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys` |
| Android release / API | Android 17 / API 37 |
| Security patch | 2026-05-05 |
| ABI | `x86_64` (`x86_64,arm64-v8a` supported list) |
| Page size | 4096 bytes |
| RAM | 576 MiB reported by `getprop dalvik.vm.heapsize`; host allocation not measured |
| Display | 2880×1800 at 240 dpi |
| WebView | Not recorded for this profile |
| Characteristics | `emulator` |

## Limits

- No native desktop input, caption, IME, thermal or physical ABI evidence is
  inferred from this emulator.
- Memory figures from `dalvik.vm.heapsize` are not total process/RAM budgets.
- The emulator is useful for x86_64 load/query execution and layout regression,
  not for physical-device acceptance.
