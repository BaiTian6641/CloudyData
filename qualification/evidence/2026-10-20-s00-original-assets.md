# S00.2 original Cloudy asset provenance reconciliation

**Date:** 2026-10-20 (America/Chicago)
**Requirements:** S00.2; Cloudy design authority and scoped token provenance.
**Status:** verified for the local path/hash discovery and common-token
comparison below. Token adoption, rendered visual acceptance and full S00
acceptance remain separate gates.
**Cloudy Data source inspected:** `50dd16fffbd8b439c29ee87d08c2671567bbd485`.

## Discovery and authority

The original supplied Cloudy Data packet omitted the upstream token JSON,
generators, preview and platform Rule 0 document. These assets are now locally
discoverable in the existing sibling repository
`/home/polar/Documents/ChatGPT/Cloudy Workspace`, whose observed HEAD was
`bccb328b85f891ecfeedcf7e6e491c033cb706bd`. This was a read-only inspection;
unrelated untracked files in that repository were preserved. Each of the four
source files below matched its `git show HEAD:<path>` bytes.

The original repository calls its token JSON the canonical generator input,
but the file itself has `status: proposed`. Finding it does not approve all of
its values for Cloudy Data. The document precedence and applicability map in
`docs/README.md` continue to govern. No Console/Desktop terminal, RDP, X11, USB,
host-vault, remote-fullscreen or two-app packaging behavior was imported.

All paths in the following table are relative to that sibling repository.

| Original asset | Last source commit affecting the file | SHA-256 |
| --- | --- | --- |
| `shared/design-system/tokens.json` | `e283cacaa47abc0330b7943a8c2ef6bfa51e9018` | `dabfa9d7e8bde69df0345aee62c944413ce57889bb0cf1dfa41d6c0c455a0047` |
| `tools/generate_design_tokens.py` | `e283cacaa47abc0330b7943a8c2ef6bfa51e9018` | `c1a6f08f598b7c688c471d4b0ca6396dd12e1fc70108897007e37e82201e5342` |
| `tools/generate_android_tokens.py` | `6017877fa5b2bc0169ffec740e33eb0115cc7bf6` | `490bf094a680c6394487d8c55e6b7dc857ad02135672a0e4a766828fd59f7fb6` |
| `docs/00-project/platform-rule-zero.md` | `61220c58367edda3d489704678c334ae1bde1d03` | `25b4c1e53d997757e7932ef9696cf27a0409dcde2e92fbc5da669f89da5cbd6d` |

The original design corpus is `docs/04-design/`. Its `README.md`, `tokens.css`
and `preview.html` were present; SHA-256 hashes were respectively
`29b5f07c597ef57d727a8dd3fc6a74bb1508a23ad2bed20c158e03e089e8793d`,
`2f75de94999b440b4e4077d45e93d38c816f381f32b00fd69f8e3009858eeb7a`
and `b9032ddc7b8c0d980d064c1673aaf32e296bcb8b72948b0abe4898fde41a034c`.
The preview was located without rendering or accepting it as native evidence.

## Scoped common-token comparison

A Python JSON comparison used independent original values as the expected
values and Cloudy Data's existing `design-system/tokens.json` as the observed
values. Key spelling was normalized from hyphenated original names to local
camel case. The comparison did not rewrite either file.

| Group | Explicit mapping | Checks | Result |
| --- | --- | --- | --- |
| Common appearance colors | Original `themes.<appearance>.<role>` to local `color.<appearance>.<role>`, excluding every `terminal-*` role | 46 (23 per appearance) | All equal |
| Common typography | `page`, `section`, `body`, `label`, `metadata`; original `size`/`lineHeight`/`weight` to local `sizeSp`/`lineHeightSp`/`weight` | 15 | All equal |
| Shape | Original `radii` to local `shape` by role | 6 | All equal |
| Motion | Original `motionMs.<role>` to local `motion.<role>Ms` | 3 | All equal |
| Density minimum targets | Original `density.<tier>` to local `density.<tier>.minTargetDp` | 3 | All equal |
| Spacing | Complete original/local spacing arrays | 1 | Equal |
| **Total** | | **74** | **74 equal; no mismatches** |

Excluded from this comparison: terminal/ANSI values, terminal typography,
font-family strings, local geometry, density padding, disabled-role proposals
and all chart palettes. An exact match of common numeric tokens does not
certify native component usage, typography fallback, accessibility or visual
appearance.

The original CSS generator writes its own `docs/04-design/tokens.css`; the
original Android generator targets its own
`shared/design-system/android/src/main/java/io/github/baitian6641/cloudy/design/CloudyTokens.kt`.
Neither is a Cloudy Data build entrypoint. Cloudy Data keeps
`design-system/tokens.json` as its single provisional source and
`tools/generate_tokens.py` as its generator.

## Existing Cloudy Data assets

All paths below are relative to `/home/polar/CloudyData`. These files were
already present and were unchanged by this reconciliation.

| Local asset | SHA-256 |
| --- | --- |
| `design-system/tokens.json` | `58663dcf602a2960613b8835dc145da3f38c73e588726c67596dbfca07e363ec` |
| `tools/generate_tokens.py` | `724a10e498daf0f61475efdd63a0003f83fda9c0362f14b79f267cb037b745e4` |
| `app/src/main/java/io/github/baitian6641/cloudydata/design/CloudyTokens.kt` | `008e7051a65def900d966201a4361e3a6ed206d6fbb8eb9c63bfc29c7738fc1a` |
| `app/src/main/res/font/ibm_plex_mono_regular.ttf` | `a3c50f7c0e063998cfaeae56c6169ece9e0feaffaef425aa038f85d037fb4b9b` |
| `app/src/main/res/font/ibm_plex_sans_medium.ttf` | `4cb2fecd40446639d1c241f898388590f71ab578fbc789bf41543e6b25fb6d4b` |
| `app/src/main/res/font/ibm_plex_sans_regular.ttf` | `729908583fdfe9c29398238e922e3b5a086fce703456405e60d62e503b0ec537` |
| `app/src/main/res/font/ibm_plex_sans_semibold.ttf` | `d663dc6fe873c1e9eb6a1d420c45a80c6b997954456b3c342bb99d0e59fa2c5a` |
| `licenses/IBM-Plex-OFL.txt` | `ed00326e4573277ab21aaf0580b014c95ee234b3e3144e8516e7ba76156f6507` |

Font presence and the recorded license notice are established here; font
rendering, fallback and a complete font provenance audit were not rerun.

## Focused checks and limits

- `git -C <original-root> rev-parse --show-toplevel HEAD`: confirmed the existing
  original repository and source revision above.
- `sha256sum <four-original-asset-paths>` plus Python `hashlib.sha256`: captured
  source and local asset identities.
- `git -C <original-root> log -1 --format=%H -- <path>` and `git show HEAD:<path>`:
  established last file commits and unchanged tracked bytes for the four source
  assets.
- Python common-token JSON comparison: **74/74 equal**, no mismatches.
- `python3 tools/generate_tokens.py --check`: **token output current**.
- `python3 tools/check_token_contrast.py`: **40/40** declared pairs meet their
  configured thresholds. This checks solid token pairs, not rendered states.

Only `docs/README.md` and this evidence record changed. Original source assets,
Cloudy Data tokens, generators, generated constants, fonts and application code
were not changed. The historical plans/ADRs retain their original delivery
context; this record corrects the current asset-discovery claim without silently
replacing those accepted decisions. A separate explicit token integration
decision and native review would be required before changing token authority or
adopting original-project behavior.
