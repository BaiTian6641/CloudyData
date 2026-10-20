# S03.3 desired-value favorites and native Home retry

**Date:** 2026-10-20 (America/Chicago)
**IDs:** S03.3; F01/F64; Q17/Q19/Q34; bounded Q21 Home interaction.
**Status:** verified for the bounded command/controller/native Home cases below;
S03/F64 and full Q19/Q21 remain partial. Initial failures and their
discriminating evidence are retained below.
**Source base:** `77185fdd8396fd221efb205976f2ee5a779a317a`.
**Platform:** existing API36 x86_64 `emulator-5580`; no physical Googlebook
interaction. The established Android16/4KiB/2880×1800 profile is reused without
boot, resize, density or preference changes. Licensed Tableau and16KiB runtime
remain unavailable.

## Authority, prerequisites and ownership

Reinspected actual root/main/HEAD/remotes/upstream/clean status and accepted ADRs.
Live origin main matched77185fd, with intended SSH origin unchanged. All115 files
in the preceding source/fixture manifest matched before edits. The three plans
had already been read in this session; their unchanged content and master runbook
remain authority. Original assets remain mapped to the documented sibling Cloudy
Workspace; no tokens, fonts, renderer or product-scope authority was replaced.

The recorded earliest ready task replaces Home's generic toggle with a typed
desired value over verified coordinator/CAS, atomic checkpoints, retained receipts,
durable history and failure admission. Root owns the command, coordinator,
summary revision, Home controller/UI and docs. One worker owns only the new
SetFavoriteCommandTest; another owns only HomeFavoriteUiTest. An independent
read-only reviewer assesses combined source, native results and pixels. No shared
file ownership overlaps and no new dependency or worker capability is introduced.

## Actual behavior and review correction

SetFavorite stores an explicit Boolean with frozen command/workbook/window and
expected revision. The existing coordinator atomically persists content/history/
receipt before publication. Identical retries after rename or undo return the
original acceptance and current document without writing or toggling again.
No-op acceptance adds a receipt/revision without a new history step or losing redo.

WorkbookSummary now carries its actual persisted revision. Home uses the rendered
summary value/revision to create intent. One unresolved action per workbook blocks
replacement until retry/dismissal; other workbook actions remain independent.
Successful acceptance stays pending until serialized real list-read/publication
confirms target revision. Missing/unreadable targets cannot pass refresh.
Library reload, filtering and search retain unresolved actions outside filtered
rows. Both affected row and star disable, preventing nested disabled-star taps
from opening a workbook. Retry/Dismiss labels identify the target; its label is
retained even if the library can no longer read it.

Independent review found a known-acceptance regression: a later retry failing
before a new outcome could erase the earlier accepted revision. Root preserves
that proof across retries and lets it dominate error status/copy. A real-storage
corruption/recovery-guard case discriminates this from ordinary pre-write failure.
The controller never calls known accepted work not saved. Dismiss/reload stops a
local proposal; it does not reverse the accepted favorite.

## Initial run and diagnostic correction

The first combined run executed88 cases: SetFavoriteCommandTest10 and prior77
passed; HomeFavoriteUiTest1 failed at the first search assertion. Immediately
after the native accessibility SetText action plus waitForIdleSync, the queried
text was empty rather than the exact fixture search prefix. No star operation had
yet run. Raw failure and original APK hashes are retained in initial-native.txt
and initial-artifacts.json.

The correction is test synchronization only: wait boundedly for the exact native
EditText+text(value) condition, then retain the original strict equality assertion.
Main-loop idle alone does not prove updated Compose accessibility text has been
published. There is no sleep, fallback text match, retry or skipped assertion. A
timeout preserves hierarchy/pixels before teardown for a discriminating probe.

The second combined run again passed87 command/controller/regression cases. Home
reached the failed-favorite panel but the exact star description reported enabled.
Competing hypotheses were (H1) delayed state publication, (H2) description attached
to a decorative Icon under the disabled control, and (H3) erroneous Home action
admission/row state. The next run was a focused one-case probe: wait5s for that
exact description with enabled=false, preserve XML/pixels on timeout, then retain
the strict disabled and pointer assertions. It failed the exact-node wait.

The retained diagnostic tree provesH2: description at[2793,540][2823,570] belongs
to a nonclickable enabled Icon; parent button at[2772,519][2844,591] and enclosing
row are both disabled. This rules out timing alone; the pending state is present.
The independent reviewer parsed the same nodes and confirmed the diagnosis.
Root moved CloudyIconButton's unchanged description onto its interactive Box,
added Role.Button and made its Icon decorative. Enabled/click/focus behavior,
layout and tokens are unchanged. No clearAndSetSemantics or weakened assertion.
The writable scope includes this narrow shared-component correction; combined
native regressions and an existing palette keyboard path check its wider use.
Raw second/probe failures, diagnostic hierarchy and pixels are retained.

After that correction, the focused Home run passed the exact disabled-node,
pointer containment, other-workbook reload, failure/filter retention, real Retry
and saved Favorites checks. Its final keyboard gate failed: no exact target
focused node was observed during24 immediate Tab checks. Competing explanations
were delayed accessibility focus publication, traversal skipping the nested star,
or key-event routing. A bounded discriminating probe records focused and target
node attributes before/after each real Tab, waits at most500ms for the same exact
target, preserves24 keys/strict assertion and saves XML/pixels on timeout. No
pointer/direct-focus substitute or control removal is allowed.

## Final verification

The focus probe passed1/1 in24.007s. Native trace shows root, Settings, search,
workbook row, then the exact named Button focused afterTab8. Enter persisted false
with a second exact receipt and removed the matching Favorites row. This validates
a bounded publication wait; no additional product focus change was made.

Final combined native 89/89 passed, no skips, 50.208s: favorite command/controller10,
Home 1, worksheet/admission 14, writer-death 5, metadata recovery 6, journal 12,
coordinator 14, engine/persistence/filter/View Data/dual-axis26 and palette keyboard1.
Real directory chmod0555 causes EACCES; exact bytes/revisions/receipts are checked.
The known-accepted refresh/corruption/recovery-blocked retry case stays truthful.
The Home test verifies failed request retention over a successful sibling reload,
section/search changes, disabled-star pointer containment without opening the row,
one retry/receipt, actual Favorites state and native Tab+Enter removal. No mocks.

Both x86 qualification and final default dual-ABI debug/test builds passed.
Default ZIP contains arm64-v8a+x86_64; JVM 75/75 with no skips; lint 0 errors/6 existing
warnings, including ChromeOsAbiSupport. No assertions/checks disabled. Generated
tokens current; contrast 40/40. Runtime APK SHA-256:
`0ebadc5effd73332c649a3f0dafff78269287f67fe3fa1694da341afa99a0861`;
test APK `bc749afae5eeb696965c3a1a1a9236d504855e0a68168219d6c6c9a683748d54`.

Source/artifact manifests are in artifacts/2026-10-20-s033-set-favorite/ with118
source/fixture hashes, exact APK sizes/ABIs, capture hashes/dimensions and the final
focus trace. Raw build/native results, failure logs, JVM summary, lint XML and
start-time-filtered native logcat are in android-test-results/2026-10-20-s033-set-favorite/.
Only redundant terminal blank lines at end of text logs are removed; results are
otherwise retained. Intermediate diagnostics have their own manifest and are not
represented as final acceptance captures.

Root and the independent read-only reviewer inspected the actual final native
failure/saved pixels,2880×1800 light/default-font. The target-qualified failure,
Retry/Dismiss, attention row, disabled outlined star and saved Favorites filled
star/search are visible without clipping/overlap. Independent review also parsed
the diagnostic disabled-node and exact Tab8 focus trees. No material finding
remains. These are bounded native pointer/key events and renders, not a full
physical desktop/design-matrix qualification.

## Limits

This remains ADR0009's interim JSON authority; Room, complete typed commands and
full proposal review/reapply remain open. Pending Home proposals are in memory
for that Home composition; leaving Home, recreation or whole-product death does
not restore the pending intent. Accepted content remains coordinator-authoritative.

Existing process-death regressions qualify their actual writer boundaries, not a
new favorite-specific SIGKILL, power-loss durability or concurrent live writers.
Native injected pointer/Tab/Enter and still images do not establish physical
mouse/trackpad, IME, TalkBack, all themes/densities/large text, narrow/short windows,
duplicate-name target disambiguation or complete Q19/Q21 acceptance. The original
worksheet failure-banner visual/input gate remains separate.

S11.C0 remains partial; S13.C0/S15.C0 remain absent and mandatory when applicable.
