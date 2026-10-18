# Multi-series line chart fixture: independent expectations

Source fixture: `fixtures/csv/chart_line_series.csv` (UTF-8, LF, 141 bytes,
sha256 `444353014313edf0653d7d20b6225efc88f599afe8da1379089df32c07fa4646`).

Derived with Python's `csv` module directly from the CSV; no application code
or chart implementation was consulted.

- Header: `series,event_date,value`
- Data rows: 6
- Distinct series: 2 (`North`, `South`)

## Ordered points per series

Ordering is ascending by `event_date`; exactly one point per observed date.

North:
1. 2026-01-01 = 10
2. 2026-02-01 = 20
3. 2026-03-01 = 30

South:
1. 2026-01-01 = 1
2. 2026-02-01 = 2
3. 2026-04-01 = 4

South has no `2026-03-01` row. That gap MUST stay a gap in the line: it is
absent data, NOT an implicit zero, NOT an interpolated point, and NOT a
zero-valued vertex. The South line connects 2026-02-01 to 2026-04-01 (or is
broken) according to the renderer's absent-data policy; either way no March
point exists.

## Totals

- North total: 10 + 20 + 30 = 60
- South total: 1 + 2 + 4 = 7
- Overall sum: 60 + 7 = 67
- Point count: North 3, South 3, total 6

## Exact-decimal note

All values are integers. No exact-decimal value was added because the
prescribed South points are fixed (2026-04-01 = 4) and inserting a decimal
would either change a prescribed value or add an extra point, which would
complicate the line expectation. Integer arithmetic above is exact.
