# ADR 0006 — Import operation receipts and rollback

**Date:** 2026-10-18
**Status:** accepted for the current local import path

## Context

Room/metadata and the external Parquet snapshot are not one ACID transaction.
The architecture requires a durable state machine, reconciliation on restart and
no false completed state. The initial import moved data files before writing
workbook metadata and had no recovery path.

## Decision

Every applied import writes `staging/<operationId>/receipt.json` with:

- operation and workbook IDs;
- proposed snapshot ID;
- phase: `IntentRecorded`, `DataPrepared`, `DataCommitting`, `DataCommitted`,
  `MetadataPublished`;
- row count, staged Parquet byte size and SHA-256;
- whether a previous snapshot was backed up.

Sequence:

1. Record intent.
2. Write and verify staged Parquet; compute its hash; record `DataPrepared`.
3. Move any existing accepted snapshot/original into the operation's `previous/`
   directory.
4. Record `DataCommitting`.
5. Atomically move the new Parquet and original into `data/` and `source/`.
6. Record `DataCommitted`.
7. Save `workbook.json` with the proposed snapshot ID.
8. Record `MetadataPublished`; remove the operation directory.

On workbook load, `ImportRecovery` reconciles each operation directory:

- `IntentRecorded` or `DataPrepared`: discard the operation; accepted data was
  not touched.
- `DataCommitting`/`DataCommitted` and the workbook already names the proposed
  snapshot: keep the committed data, remove operation files.
- `DataCommitting`/`DataCommitted` and metadata does not name it: restore the
  previous files from `previous/`; if no previous snapshot existed, remove the
  newly published files.
- `MetadataPublished`: remove operation files.
- Unreadable metadata: retain operation directories for diagnosis.

`apply` invokes reconciliation immediately on its own failure, then reports the
original failure with any recovery message. `workbook.json` writes remain
temporary-file + fsync + atomic rename.

## Tests

`RecoveryTest` covers interrupted commit rollback, published-metadata retention
and prepared-operation cleanup. These are constructed file/receipt states, not
real process kills. `CancellationTest` covers native query interruption
separately.

## Limits

- No fault injection at every fsync/rename boundary; directory fsync is not
  guaranteed.
- A process kill during `store.save` can still leave ambiguity; reconciliation
  uses the metadata content to decide, which is the intended evidence.
- Previous data is deleted after a successful import; there is no user-visible
  version history or undo yet.
- No Room command journal or multiwindow revision protocol.
