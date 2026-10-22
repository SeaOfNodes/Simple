# Chapter backport queue

This file tracks pending backports and deferred work. Completed work and old
validation results belong in Git history. Reusable implementation constraints
live in the [AI notes](../skills/chapter25-codex-notes.md).

The [approved chapter split](chapter-split.md) owns the teaching sequence for
the current Chapters 24 and 25. Use descriptive chapter names while the count
changes. Cliff approved starting with the proposed SCCP and construction/escape
boundaries; revisit construction/escape after he can run both snapshots.
Library construction and optimizer tuning move to appendices.

The first named snapshots are `chapter-sccp`, `chapter-interprocedural-sccp`,
`chapter-incomplete-types`, and `chapter-construction`. Local SCCP keeps unknown callers and the call
result summaries from pessimistic analysis; the next chapter discovers calls
and widens recursive arguments and returns. Numbered Chapters 24 and 25 remain
comparison baselines during extraction.

The incomplete-type snapshot groups numeric modes, conversions, guards,
symbolic fields, and unbounded Load/Phi nodes. Construction now has hidden
initializer/allocator calls, local field values and Phis, exit Stores, and
explicit publication. Private memory carries fields between helpers. Next
extract escape analysis and review the limits on early exposure of `self`. Chained comparisons still need their move to Chapter 23;
allocator tuning and native library material still need their appendices.
Root Make includes named snapshots in checks. Linear-history publication keeps
the numbered sequence until the complete replacement sequence is ready; switch
its ordering and index links when retiring the comparison baselines.

## Investigate next

Establish an earlier-chapter failure before changing compiler behavior.

### SCCP function revival: Chapter 24

`Opto.linkCG` uses `code.link(fidx)`, which clears a linker entry when its
function is structurally dead. Chapter 25 uses the non-cleaning `_link(fidx)`
while discovering calls, so temporarily disconnected functions can be revived.
Chapter 24 already has `_link`; the question is whether its own unlinking and
SCCP sequence can expose the same failure.

Trace a function from removal of its unknown-caller edge through discovery of
a real call. Preserve the distinction between unreachable control and a
structurally deleted node. No earlier-chapter failure is established yet.
Dependency propagation already exists in Chapter 24's SCCP; only port a
specific missing dependency with a reproducer. Keep Chapter 25's escape and
external-caller machinery out of this investigation.

Investigation on 2026-10-19 found no cleanup loss. A shadow Opto checked the
linker entry before and after every `linkCG` lookup while running all 513
Chapter 24 tests: 787 SCCP runs, 297 lookups, 74 links to functions with only
their null input remaining, and zero structurally dead targets or removed
entries. All tests passed. The remaining null lookups are not evidence of a
cleanup loss: their entries were already null before the lookup.

The factorial trace shows the distinction: FunNode#1309 loses Start and has
one input, four uses, and type TOP; it then reaches XCONTROL before a real
call links it. `isDead()` stays false, since it requires no inputs, no uses,
and a null type. Thus the observed temporary lack of callers does not trigger
`link()` cleanup. Keep this candidate unconfirmed rather than backporting the
Chapter 25 lookup change on textual differences alone.

## Deferred until the Chapter 25 split

### Deterministic inlining

The parse-size cutoff can reject a function whose optimized body is small.
After shrinking rewrites converge, choose one growing inline using current
body sizes and a deterministic ranking. Cache sizes per convergence round;
use stable call-site provenance to break ties, not worklist order or newly
allocated node IDs. Avoid per-node dependencies and repeated whole-body scans
while a large function gradually shrinks.

The bounded boilerplate-inlining heuristic is already in Chapters 20-24.
This item is the larger Chapter 25 redesign.

### Conversion and BOTTOM-on-error contracts

Coordinate these changes with the incomplete-type/conversion split:

- Chapter 24 Loads supply a typed result even when they report an error.
  Changing that result to BOTTOM alone breaks incomplete loop typing and can
  obscure the intended diagnostic. Chapter 25's Convert nodes provide the
  surrounding typed result. Relevant existing cases include
  `testBrainFuck`, `testWhileWithNullInside`, `testNullGuardErrors`, and
  `testLinkedList0`.
- Chapter 24 arithmetic also needs its typed fallback while forward references
  are unresolved. Returning BOTTOM confuses unresolved expressions with
  uninitialized fields (`testCoRecur`) and loses arithmetic diagnostics.

Preserve the current contracts until the parser, conversions, loop bounds,
and diagnostics can move together. Do not weaken narrowing assertions or
expected errors to make an isolated backport pass.

### Representation changes

The approved split keeps incomplete types, numeric modes, conversions, guards,
and symbolic fields together, then introduces private constructor memory in
the construction chapter. They are not independent fixes to copy into earlier
snapshots.

Serialization, global identity remapping, and imported escape summaries belong
with separate compilation. Escape analysis gets its own earlier chapter after
construction; its extraction boundary is still to be established.

## Work that stays in Chapter 25

Class-cycle checking and CLInit runtime lowering stay in Chapter 25. The
[chapter README](../chapter25/README.md#lazy-class-initialization) explains the
implementation; future work lives in the
[roadmap](../chapter25/ROADMAP.md#class-initialization-and-compiler-follow-ups).
Native code cannot receive classes; a native-startup/class-handoff policy is
not planned. Current data pointers remain eight bytes.

## Backport validation

Introduce an independent fix in the earliest applicable chapter and propagate
it through every affected snapshot. For correctness fixes, first reproduce the
failure in the destination compiler. Preserve each chapter's representation
and tutorial boundary.

Run focused checks and the existing full Make suites for the affected compiler
directories, for example:

```sh
make -k tests CHAPTERS="chapter20 chapter21"
```

Chapter 25's inherited tests do not validate earlier compiler implementations.
Force rebuilds when older Makefiles miss dependencies, and rebuild serialized
libraries when their compiler or object format changes. Keep execution and
register-legality checks independent of spill expectations. Preserve unrelated
edits, keep text LF-only, and run `git diff --check`.
