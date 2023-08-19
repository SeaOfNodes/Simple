# Optimizer and backend tuning

These measurements are from the numbered snapshots before the 2026-10 split.
In this historical table, Ch 24 means the retired combined SCCP snapshot and
Ch 25 means the retired combined modules/constructor/import snapshot.
They are not new measurements of the extracted chapters. The implementation
continues in [Separate Compilation](../chapter28b/README.md).

## RegAlloc improvements: try cold loop splits first

A loop Phi and its backedge can belong to one live range even though their
values must coexist at some instruction.  This self-conflict requires splitting
the range.  Chapter 23 split the Phi's definition and incoming values right away.
This chapter first leaves the hot definition/backedge alone and tries the
outside-loop copies.  If coloring succeeds, it avoids moves on every iteration.
Other conflicts, including two-address operations, keep their existing handling.

The allocator remembers which Phis have had this chance.  If the same Phi
self-conflicts again, the next attempt uses the ordinary aggressive splitting.
The record uses Phi identity because live ranges are rebuilt each round.  It
also covers a backedge-only conflict where the first attempt inserts no copy:
that attempt must still consume the one permitted delay.  A regression checks
both the cheaper first attempt and the mandatory fallback.

## RegAlloc improvements: live-range area and split cost

Chapter 24 tried cold copies before splitting a loop Phi's hot edges.  This
chapter also estimates how much scheduled code a spill frees from register
pressure, divided by the estimated cost of the new copies.  Callee-save ranges
cover a whole function.  Cloneable constants can be rebuilt nearer their uses.
Copies inside loops cost more: the estimate weights them by `8^loopDepth`.
These are inexpensive approximations, especially for values with many uses.

A single-use function address in the same block gets priority for
rematerialization when it is separated from its use.  Moving that address
calculation next to the use adds no instruction and avoids preserving a
callee-save register merely to hold the constant.  Adjacent uses and multi-use
addresses retain the area/cost ranking.

Rows are fixed test cohorts; columns are compiler chapters. Each cell gives
**Ops / RA / X**: executed ARM+RISC-V instructions, the subset emitted for
allocator copies/rematerializations, and x86's loop-weighted split-move estimate.
**Cases D / X** counts measured dynamic program/target cases and x86 compilations;
membership and inputs stay fixed across columns. Native-library work is excluded.
Chapter 20 has no encoder, hence no dynamic counts. Lower is better within each
metric; see the [measurement details](../regalloc-spills.md) for coverage.

<!-- spill-matrix:start -->
| Test cohort | Cases D / X | Ch 20: Ops / RA / X | Ch 21: Ops / RA / X | Ch 22: Ops / RA / X | Ch 23: Ops / RA / X | Ch 24: Ops / RA / X | Ch 25: Ops / RA / X |
|---|---:|---:|---:|---:|---:|---:|---:|
| Ch 20 | 22 / 13 | — / — / 161 | 55,173 / 5,407 / 174 | 58,751 / 9,099 / 175 | 58,736 / 9,084 / 184 | 58,720 / 9,068 / 183 | 58,965 / 9,076 / 208 |
| Ch 21 | 20 / 18 |  | 6,207 / 261 / 461 | 6,324 / 379 / 475 | 6,326 / 381 / 475 | 6,325 / 380 / 470 | 6,196 / 219 / 534 |
| Ch 22 | 14 / 8 |  |  | 198 / 60 / 7 | 194 / 56 / 7 | 194 / 56 / 7 | 252 / 92 / 11 |
| Ch 23 | 14 / 8 |  |  |  | 630 / 160 / 36 | 630 / 160 / 36 | 864 / 358 / 36 |
| Ch 24 | 36 / 20 |  |  |  |  | 4,923 / 1,341 / 790 | 4,646 / 1,335 / 609 |
| Ch 25 | 2 / 12 |  |  |  |  |  | 20 / 0 / 1,856 |
<!-- spill-matrix:end -->


Chapter 25 introduces several features, which when run on the older tests
generate a lot more code, which in turn generates more spills.

Class initialization, inlining, escape analysis, and library adaptations
all change the graphs.  To help with this, we adjust the inlining
heuristics to try to inline and optimize the boilerplate.
