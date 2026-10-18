# Renderer vendor provenance

- Vega `6.4.0` from `https://registry.npmjs.org/vega/-/vega-6.4.0.tgz`
  - tarball SHA-256 `3521fb6ff68a94a40323954f0ba4484ba4014b157bfd37f89a3b063082320c42`
  - `build/vega.min.js` SHA-256 `8f6a3587cf8d4f42c7e08120e3eb05d067e746d554e39d2dcf52acc0bd5ba28f`
- vega-interpreter `2.3.2` from `https://registry.npmjs.org/vega-interpreter/-/vega-interpreter-2.3.2.tgz`
  - tarball SHA-256 `b83489633c1320e9d6f839781f0c84c661a5ca2666fe38cb83f7c52a531fffec`
  - `build/vega-interpreter.js` SHA-256 `c0bdb735a63f9276f136e8d90d7fdf5d8e9df8031eb069048fc9975c876fd7f9`
- generated UMD wrapper SHA-256 `ad3b3076d749ff1e378f4f45ff30f1dce97d3c1465c30a7d7c21f8fcbb7b15e5`

Both packages are BSD-3-Clause. License texts are in `licenses/`. The wrapper
inlines only `ascending`, `isString` and `DisallowedObjectProperties` from
vega-util 1.17.4 and exposes `vega.expressionInterpreter`; it does not enable
the Function constructor path. Assets are served only from the app-asset
origin; no CDN or network fallback is permitted.
