# ADR 0003 — Local workbook and snapshot authority

**Date:** 2026-10-18
**Status:** accepted provisionally for R1; full transaction recovery is pending S03/S04

## Context

The architecture forbids two writers and warns that a DuckDB `COMMIT` does not
make an external Parquet/Arrow write transactional. The current slice needs a
durable local import, exact query, save/reopen, and an honest failure boundary.

## Decision

For the current local slice:

- `workbook.json` in app-private storage is the document metadata authority.
  It contains a versioned document model, stable workbook/worksheet/field IDs,
  source provenance, snapshot reference and revision.
- `data/snapshot.parquet` is the authoritative imported dataset for queries.
  `source/original.csv` preserves the staged input bytes.
- DuckDB runs with an in-memory database per workbook session and reads the
  Parquet snapshot for each query. There is no live `.duckdb` file or WAL to
  reconcile, and no second file writer.
- Import publishes in this order:
  1. stage the source under the workbook's `staging/<operation>/` directory;
  2. inspect schema/preview;
  3. write a complete staged Parquet file and fsync it;
  4. verify the row count by reading the staged file;
  5. atomically move the Parquet and original into `data/` and `source/`;
  6. atomically write `workbook.json`.
- Save writes a temporary JSON file, fsyncs it, and atomically renames it over
  the existing metadata file.
- A failed save is reported as `Not saved`; the UI does not claim success.
- A failure after the data move but before metadata publication can leave
  orphaned files. Recovery/reconciliation is not yet implemented; this is an
  explicit S03/S04 gap, not a completed durability claim.
- Replacing a data source currently overwrites the previous snapshot and
  original. A previous-version retention/undo path is not implemented; the
  replacement dialog states this.

## Rejected alternatives

- ZIP a live DuckDB database file: rejected because WAL/checkpoint semantics are
  not a portable snapshot and replacement is not atomic.
- Treat the Parquet write as rollback-safe because a surrounding SQL transaction
  succeeded: rejected; the file is external to DuckDB's transaction.
- Use Room plus DuckDB in one presumed transaction: rejected; the two stores are
  not one ACID transaction and no such claim is made.

## Pending

- Operation receipt state machine and crash reconciliation (S03/S04).
- Previous snapshot retention and undo for accepted imports.
- Room metadata integration and migration.
- Multiwindow coordinator/single-writer registry.
