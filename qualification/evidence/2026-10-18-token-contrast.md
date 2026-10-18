# Token contrast check evidence

**Date:** 2026-10-18
**Command:** `python3 tools/check_token_contrast.py`
**Result:** 40/40 declared solid pairs meet their configured threshold (exit 0).
**Source:** `design-system/tokens.json` (provisional)

## Coverage

- Primary text on canvas, surface, muted and raised surfaces.
- Secondary text on the same four surfaces.
- On-primary/primary, on-primary-container/container,
  on-accent-container/container.
- Success, warning and danger foregrounds on their supplied containers.
- Outline and focus against surface/canvas; focus against selected container.
- Proposed disabled text/background.

The light disabled foreground was adjusted from the initially proposed `#7C8C91`
(2.94:1) to `#58696E` (4.84:1) so disabled labels remain readable. The dark pair
is `#8FA1A7`/`#20343A` at 4.85:1. This is a proposed role, not an upstream
canonical value.

## Limits

- This checks declared solid color pairs, not rendered states, opacity, overlays,
  chart series, focus rings on arbitrary backgrounds, or screenshots.
- It is not a WCAG conformance certification.
- The canonical upstream token source remains absent; see ADR 0002.
