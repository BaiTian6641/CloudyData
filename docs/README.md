# Cloudy Data documentation map

Read in this order:

1. `plans/Cloudy_Data_01_Product_and_Design_Contract.md` — product scope,
   capability families F01–F72, screen and desktop contracts.
2. `plans/Cloudy_Data_02_Architecture_and_Technical_Specification.md` —
   selected architecture, data/command contracts, engine/renderer boundaries.
3. `plans/Cloudy_Data_03_Implementation_Roadmap_and_Agent_Runbook.md` — stages
   S00–S22, fixture families Q01–Q34, acceptance and evidence rules.
4. `design/` — the supplied Cloudy design handbook and contracts. The design
   corpus is authority within its stated scope. Console terminal copy-on-select,
   right-click paste, RDP/X11 input capture, USB ownership, remote fullscreen and
   host-centric home documents are **not** Cloudy Data requirements. Only the
   applicable Cloudy principles, tokens, layout, component, interaction,
   accessibility and implementation guidance are reused.
5. `design/chart-grammar-and-customization.md` — owner-directed chart family,
   layering, axis/grid/title/legend/label/color and export contract; atomic
   status is in `parity/chart-catalog.yaml`.
6. `decisions/` — accepted project decisions, including explicit amendments and
   qualification limits.

## Supplied design files (title → actual path → applicable scope)

| ID | Title | Actual path | Cloudy Data scope |
| --- | --- | --- | --- |
| CD01 | Design handbook README | `design/README.md` | Direction, Rule 0 desktop scope, token ownership. Terminal/RDP references excluded. |
| CD02 | Principles | `design/principles.md` | Productive structure, tonal hierarchy, truthful state. Host/USB examples are references only. |
| CD03 | Visual foundations | `design/foundations.md` | Supplied color pairs, type, spacing, shape, motion. Terminal palette excluded from product use. |
| CD04 | Layout and density | `design/layout.md` | Current-window geometry, pane thresholds, three density tiers. Console/Desktop arrangements excluded. |
| CD05 | Component contracts | `design/components.md` | Shared controls, feedback precedence, focus/state rules. Host/USB/file-transfer components excluded. |
| CD06 | Interaction | `design/interaction.md` | Focus ownership, shortcut precedence, IME, recovery. Terminal copy/paste and remote input excluded. |
| CD07 | Screen specifications | `design/screen-specs.md` | Screen-contract method and state discipline. S01–S17 Console/Desktop screen IDs are not Cloudy Data screens. |
| CD08 | Accessibility | `design/accessibility.md` | Contrast, keyboard, scaling, motion, native evidence. |
| CD09 | Implementation mapping | `design/implementation-map.md` | Token generation, Compose wrappers, native grid, renderer boundary. |
| CD10 | Desktop workflows | `design/desktop-workflows.md` | General desktop discipline only. SFTP/X11/RDP/USB workflows are out of scope. |
| CD11 | Native workspace review | `design/native-workspace-review.md` | Review procedure and evidence boundaries; its Console/Desktop results are not reused. |
| CD12 | Worker visual acceptance | `design/worker-visual-acceptance.md` | Rendered native evidence, independent review, honest capability states. |
| CD13 | External feedback review | `design/2026-10-16-external-feedback-review.md` | Approved corrections only; no fourth density tier, palette replacement or EDA scope. |
| CD14 | Workspace home revision | `design/2026-10-16-workspace-home-revision.md` | Home organization principles only; host/vault/terminal decisions are not imported. |
| CD15 | Native display settings | `design/native-display-settings.md` | Three density tiers and System/Light/Dark; its persistence implementation is not copied. |
| CD16 | Terminal interaction contract | `design/desktop-terminal-interaction-contract.md` | Out of scope; source of input-boundary discipline only. |
| CD17 | RDP interaction contract | `design/desktop-rdp-interaction-contract.md` | Out of scope. |
| CD18 | Remote session presentation | `design/remote-session-presentation.md` | Out of scope. |

## Original assets and provisional token authority

The original supplied packet omitted the referenced token JSON, generators,
fonts, previews and platform Rule 0 document. A read-only local discovery on
2026-10-20 located the original Cloudy Workspace repository at
`/home/polar/Documents/ChatGPT/Cloudy Workspace`, observed at commit
`bccb328b85f891ecfeedcf7e6e491c033cb706bd`. Its relevant assets are:

| Asset | Path relative to the original Cloudy Workspace repository | Cloudy Data use |
| --- | --- | --- |
| Original token source | `shared/design-system/tokens.json` | Compare applicable common roles; the original source itself says `status: proposed`. |
| CSS generator | `tools/generate_design_tokens.py` | Provenance for the original design specimen; not Cloudy Data's generator. |
| Android generator | `tools/generate_android_tokens.py` | Provenance for the original Console/Desktop constants; not run against Cloudy Data. |
| Original design corpus and specimen | `docs/04-design/` | Original source for the scoped corpus above; the specimen remains a design study. |
| Original desktop Rule 0 | `docs/00-project/platform-rule-zero.md` | Desktop/Android context only; its two-app, host, terminal and remote-session requirements do not transfer. |

The scoped common-token comparison matched **74/74** checks: 23 color roles in
each appearance, five typography roles, spacing, shape, motion and density
minimum targets. Cloudy Data's disabled roles, chart palettes and other local
additions remain provisional. Discovery does not replace this product's
document authority or accept the original project's implementation evidence.

`design-system/tokens.json` remains Cloudy Data's single provisional source,
with `tools/generate_tokens.py` as its generator. Do not hand-edit generated
`CloudyTokens.kt` or substitute either original generator. Packaged IBM Plex
Sans/Mono fonts already exist in `app/src/main/res/font/`, with the recorded OFL
notice in `licenses/IBM-Plex-OFL.txt`. Paths, source commits, hashes, comparison
method and remaining limits are recorded in
[`2026-10-20-s00-original-assets.md`](../qualification/evidence/2026-10-20-s00-original-assets.md).

## Planning-file relocation

The three plan files were moved from `docs/` to `docs/plans/` before the first
commit. Their relative links to each other are preserved. No content was
rewritten; status claims in them remain planning claims, not evidence.
