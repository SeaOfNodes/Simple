# Chapter backport queue

This file tracks pending backports and deferred work. Completed work and old
validation results belong in Git history. Reusable implementation constraints
live in the [AI notes](../skills/chapter25-codex-notes.md).

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

### Other candidates requiring a concrete earlier-chapter failure

These are investigation candidates, not promised wholesale ports:

| Candidate | Boundary to establish |
|---|---|
| Conditional Store and array Load control | Reproduce under the destination chapter's alias model. |
| SCCP dependencies, function revival, reachability | Separate corrections applicable to Chapter 24 from Chapter 25's guards, escape analysis, and external-caller machinery. |
| Incomplete types, numeric modes, guards, symbolic fields, private constructor memory | Choose chapter boundaries and move the required invariants together. |

Serialization, global identity remapping, and module escape summaries stay
with separate compilation. Escape analysis stays in Chapter 25 pending the split.

## Chapter 25 follow-ups

Class-cycle checking and CLInit runtime lowering stay in Chapter 25. The
[chapter README](../chapter25/README.md#lazy-class-initialization) explains the
implementation; future work lives in the
[roadmap](../chapter25/ROADMAP.md#class-initialization-and-compiler-follow-ups).
Native code cannot receive classes; a native-startup/class-handoff policy is
not planned. Current data pointers remain eight bytes.

Allocator tuning and additional emulator measurement coverage are closed.
Current allocation measurements live in [regalloc-spills.md](regalloc-spills.md).

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
