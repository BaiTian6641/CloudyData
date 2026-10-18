#!/usr/bin/env python3
"""Check declared solid token pairs. This is not a rendered-state or WCAG audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = json.loads((ROOT / "design-system" / "tokens.json").read_text())["color"]

TEXT_THRESHOLD = 4.5
NON_TEXT_THRESHOLD = 3.0

# (foreground, background, threshold, label)
PAIRS = [
    ("textPrimary", "canvas", TEXT_THRESHOLD, "primary text / canvas"),
    ("textPrimary", "surface", TEXT_THRESHOLD, "primary text / surface"),
    ("textPrimary", "surfaceMuted", TEXT_THRESHOLD, "primary text / muted"),
    ("textPrimary", "surfaceRaised", TEXT_THRESHOLD, "primary text / raised"),
    ("textSecondary", "canvas", TEXT_THRESHOLD, "secondary text / canvas"),
    ("textSecondary", "surface", TEXT_THRESHOLD, "secondary text / surface"),
    ("textSecondary", "surfaceMuted", TEXT_THRESHOLD, "secondary text / muted"),
    ("textSecondary", "surfaceRaised", TEXT_THRESHOLD, "secondary text / raised"),
    ("onPrimary", "primary", TEXT_THRESHOLD, "on-primary / primary"),
    ("onPrimaryContainer", "primaryContainer", TEXT_THRESHOLD, "on-primary-container / container"),
    ("onAccentContainer", "accentContainer", TEXT_THRESHOLD, "on-accent-container / container"),
    ("success", "successContainer", TEXT_THRESHOLD, "success / container"),
    ("warning", "warningContainer", TEXT_THRESHOLD, "warning / container"),
    ("danger", "dangerContainer", TEXT_THRESHOLD, "danger / container"),
    ("outline", "surface", NON_TEXT_THRESHOLD, "outline / surface"),
    ("outline", "canvas", NON_TEXT_THRESHOLD, "outline / canvas"),
    ("focus", "surface", NON_TEXT_THRESHOLD, "focus / surface"),
    ("focus", "canvas", NON_TEXT_THRESHOLD, "focus / canvas"),
    ("focus", "primaryContainer", NON_TEXT_THRESHOLD, "focus / selected"),
    ("disabledForeground", "disabledBackground", TEXT_THRESHOLD, "disabled text (proposed)"),
]


def rgb(value: str) -> tuple[float, float, float]:
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) / 255.0 for i in (0, 2, 4))


def luminance(value: str) -> float:
    channels = []
    for channel in rgb(value):
        channels.append(channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4)
    r, g, b = channels
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: str, b: str) -> float:
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def main() -> int:
    failures = 0
    total = 0
    for theme in ("light", "dark"):
        print(f"\n{theme.upper()}")
        for fg, bg, threshold, label in PAIRS:
            total += 1
            ratio = contrast(TOKENS[theme][fg], TOKENS[theme][bg])
            passed = ratio >= threshold
            if not passed:
                failures += 1
            print(f"  {'PASS' if passed else 'FAIL'} {ratio:5.2f}:1  {label}")
    print(f"\n{total - failures}/{total} declared pairs meet their configured threshold.")
    print("Disabled text is exempt from WCAG contrast minima; its proposed pair is still reported.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
