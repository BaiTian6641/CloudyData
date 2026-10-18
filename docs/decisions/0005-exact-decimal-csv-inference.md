# ADR 0005 — Exact decimal CSV inference

**Date:** 2026-10-18
**Status:** accepted for plain decimal notation

## Observed failure

DuckDB's default CSV sniffer inferred a column with `0.10`/`0.20` as `DOUBLE`.
That loses exact decimal semantics and violates Q02's requirement that decimal
values survive without binary floating-point coercion. Reading all columns as
VARCHAR and removing DOUBLE from the candidate list preserved some decimals but
misclassified leading-zero identifiers such as `00123` as DECIMAL.

## Decision

1. Keep DuckDB's default inference for the initial schema. This correctly keeps
   leading-zero identifiers as VARCHAR in the Q01 fixture.
2. For every column inferred as DOUBLE, run one full-file scan over the source
   read as VARCHAR. For each such column, compute:
   - count of non-empty values that are not plain decimal notation
     (`^[+-]?(\d+(\.\d*)?|\.\d+)$`);
   - maximum fractional digits;
   - maximum integer digits.
3. If all non-empty values are plain decimals and precision/scale fit DECIMAL(38),
   cast the column to `DECIMAL(max(18, integerDigits + scale), scale)` during
   Parquet publication. Preserve the resulting scale in the snapshot schema.
4. Otherwise retain DOUBLE and surface a warning. Scientific notation,
   localized separators and values exceeding DECIMAL(38) are not silently
   accepted as exact.
5. Verify the final Parquet schema and row count before publishing metadata.

## Evidence

- Q02 device test asserts exact `0.30` for `0.10 + 0.20` via JNI and via a
  worksheet SUM over the imported Parquet snapshot.
- Q01 device test asserts `00123` remains the VARCHAR `"00123"`, embedded
  newlines/quotes survive, and the empty note parses as NULL.
- CSV decimal `0.10` is imported as `DECIMAL(18,2)` and displayed as `0.10`.

## Limits

- The scan is a full pass over the staged file; performance is not yet measured
  for D1/D2.
- Only one decimal convention (dot) is supported. Locale-aware import options are
  pending S04.
- A column with mixed plain decimals and exponent notation remains DOUBLE.
- Precision is capped by DuckDB's DECIMAL(38) limit; larger exact numerics need a
  separate type path.
- The schema/preview is valid for the file as staged at inspection time; a change
  to the file after staging is not possible through this path, but refresh/source
  replacement semantics remain pending.
