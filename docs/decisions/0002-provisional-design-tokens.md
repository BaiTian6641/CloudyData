# ADR 0002 — Provisional token source

**Date:** 2026-10-18
**Status:** accepted provisionally; canonical asset still missing

## Context

The supplied design corpus states that `shared/design-system/tokens.json` and
its generators are canonical. Neither the JSON file nor the generator history,
fonts, previews or `../00-project/platform-rule-zero.md` were present in the
supplied directory. The numeric values in `docs/design/tokens.css` and the
product contract are the only delivered values.

File 01 requires a clearly marked provisional token source and a missing-token
register rather than inventing canonical values.

## Decision

- Create `design-system/tokens.json` with the supplied numeric colors,
  typography, spacing, radii, motion and density tiers.
- Mark the file `status: provisional` and record its provenance.
- Add explicit **proposed** disabled roles and chart palettes because components
  and visual marks need explicit states; label them proposed in the file and
  never describe them as upstream canonical.
- Generate `CloudyTokens.kt` only through `tools/generate_tokens.py`; CI can run
  `--check`.
- Keep body text at 14 sp with 20 sp line height in every density tier. Density
  changes padding and minimum targets, not text size.
- Use packaged IBM Plex Sans/Mono unmodified under the supplied OFL terms, with
  Android/system fallback for uncovered scripts.

## Missing roles / proposed additions

- `disabledForeground`, `disabledBackground`, `disabledBorder` (proposed; not
  supplied).
- Chart categorical/sequential/diverging/missing palettes (proposed additions,
  explicitly separated from operational success/warning/danger).
- A dedicated navigation-hover token; `surfaceRaised` is used provisionally.
- Elevation/shadow tokens.
- Map/tile colors and geographic data colors.

## Verification

- The declared supplied pairs were not independently re-audited as a full
  contrast certification. A script check is recorded in
  `qualification/evidence/2026-10-18-token-contrast.md`; it covers solid token
  pairs only, not rendered states or arbitrary overlays.
- Native screenshots on the real device show the palette and typography; they do
  not establish contrast conformance, TalkBack behavior or physical input.
- Do not import Cloudy Console palettes or a fourth density tier. The canonical
  design review remains outstanding until the real upstream asset is supplied.
