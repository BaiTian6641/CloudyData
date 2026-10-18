#!/usr/bin/env python3
"""Fetch and pin the offline Vega renderer assets.

Vega 6.4.0 and vega-interpreter 2.3.2 are pinned by registry tarball hash. The
interpreter package ships an ES module; this script wraps its verified body as a
UMD-style global (`vega.expressionInterpreter`) without a bundler. The HTML and
host bridge are hand-authored beside the generated vendor files.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSET_DIR = ROOT / "app/src/main/assets/renderer"
LICENSE_DIR = ROOT / "licenses"
VEGA_VERSION = "6.4.0"
INTERP_VERSION = "2.3.2"
VEGA_URL = f"https://registry.npmjs.org/vega/-/vega-{VEGA_VERSION}.tgz"
INTERP_URL = f"https://registry.npmjs.org/vega-interpreter/-/vega-interpreter-{INTERP_VERSION}.tgz"
VEGA_TARBALL_SHA256 = "3521fb6ff68a94a40323954f0ba4484ba4014b157bfd37f89a3b063082320c42"
INTERP_TARBALL_SHA256 = "b83489633c1320e9d6f839781f0c84c661a5ca2666fe38cb83f7c52a531fffec"

HELPERS = """\
(function (global) {
  'use strict';
  // Inlined from vega-util 1.17.4, which the interpreter imports. Keeping the
  // three tiny primitives local avoids a second global Vega bundle.
  const ascending = (a, b) => a < b ? -1 : a > b ? 1 : a >= b ? 0 : NaN;
  const isString = (value) => typeof value === 'string';
  const DisallowedObjectProperties = new Set(Object.getOwnPropertyNames(Object.prototype));
"""


def fetch(url: str, expected_sha: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as response:
        payload = response.read()
    actual = hashlib.sha256(payload).hexdigest()
    if actual != expected_sha:
        raise SystemExit(f"SHA-256 mismatch for {url}: {actual} != {expected_sha}")
    return payload


def extract_member(tarball: bytes, name: str) -> bytes:
    with tarfile.open(fileobj=io.BytesIO(tarball), mode="r:gz") as archive:
        for member in archive.getmembers():
            if member.name.endswith(name):
                handle = archive.extractfile(member)
                if handle is None:
                    break
                return handle.read()
    raise SystemExit(f"missing {name} in tarball")


def check() -> int:
    expected = {
        ASSET_DIR / "vega.min.js": "8f6a3587cf8d4f42c7e08120e3eb05d067e746d554e39d2dcf52acc0bd5ba28f",
        ASSET_DIR / "vega-interpreter.js": "ad3b3076d749ff1e378f4f45ff30f1dce97d3c1465c30a7d7c21f8fcbb7b15e5",
    }
    failures = 0
    for path, sha in expected.items():
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else "<missing>"
        if actual != sha:
            print(f"drift: {path}: {actual} != {sha}")
            failures += 1
    for required in (
        ASSET_DIR / "index.html",
        ASSET_DIR / "host.js",
        ASSET_DIR / "renderer.css",
        LICENSE_DIR / "Vega-BSD-3-Clause.txt",
        LICENSE_DIR / "Vega-Interpreter-BSD-3-Clause.txt",
    ):
        if not required.exists():
            print(f"missing: {required}")
            failures += 1
    if failures == 0:
        print("renderer vendor assets current")
    return 1 if failures else 0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        raise SystemExit(check())
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)

    vega_tarball = fetch(VEGA_URL, VEGA_TARBALL_SHA256)
    interp_tarball = fetch(INTERP_URL, INTERP_TARBALL_SHA256)
    vega_min = extract_member(vega_tarball, "build/vega.min.js")
    interpreter = extract_member(interp_tarball, "build/vega-interpreter.js").decode("utf-8")
    vega_license = extract_member(vega_tarball, "LICENSE")
    interpreter_license = extract_member(interp_tarball, "LICENSE")

    if "import { ascending, isString, DisallowedObjectProperties } from 'vega-util';" not in interpreter:
        raise SystemExit("unexpected vega-interpreter import shape")
    if "export { expression as expressionInterpreter };" not in interpreter:
        raise SystemExit("unexpected vega-interpreter export shape")
    body = interpreter.split("\n", 1)[1]
    body = body.rsplit("export { expression as expressionInterpreter };", 1)[0]
    wrapped = HELPERS + body + "\n  global.vega = global.vega || {};\n  global.vega.expressionInterpreter = expression;\n})(typeof globalThis !== 'undefined' ? globalThis : this);\n"

    (ASSET_DIR / "vega.min.js").write_bytes(vega_min)
    (ASSET_DIR / "vega-interpreter.js").write_text(wrapped)
    (LICENSE_DIR / "Vega-BSD-3-Clause.txt").write_bytes(vega_license)
    (LICENSE_DIR / "Vega-Interpreter-BSD-3-Clause.txt").write_bytes(interpreter_license)

    provenance = ASSET_DIR.parent.parent.parent.parent.parent / "renderer-web" / "PROVENANCE.md"
    provenance.parent.mkdir(parents=True, exist_ok=True)
    provenance.write_text(
        "# Renderer vendor provenance\n\n"
        f"- Vega `{VEGA_VERSION}` from `{VEGA_URL}`\n"
        f"  - tarball SHA-256 `{VEGA_TARBALL_SHA256}`\n"
        f"  - `build/vega.min.js` SHA-256 `{hashlib.sha256(vega_min).hexdigest()}`\n"
        f"- vega-interpreter `{INTERP_VERSION}` from `{INTERP_URL}`\n"
        f"  - tarball SHA-256 `{INTERP_TARBALL_SHA256}`\n"
        f"  - `build/vega-interpreter.js` SHA-256 `{hashlib.sha256(interpreter.encode()).hexdigest()}`\n"
        f"- generated UMD wrapper SHA-256 `{hashlib.sha256(wrapped.encode()).hexdigest()}`\n\n"
        "Both packages are BSD-3-Clause. License texts are in `licenses/`. The wrapper\n"
        "inlines only `ascending`, `isString` and `DisallowedObjectProperties` from\n"
        "vega-util 1.17.4 and exposes `vega.expressionInterpreter`; it does not enable\n"
        "the Function constructor path. Assets are served only from the app-asset\n"
        "origin; no CDN or network fallback is permitted.\n"
    )
    print(f"wrote Vega {VEGA_VERSION} + interpreter {INTERP_VERSION} assets")


if __name__ == "__main__":
    main()
