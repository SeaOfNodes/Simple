# Chapter backport queue

This file tracks pending backports and deferred work. Completed work and old
validation results belong in Git history. Reusable implementation constraints
live in the [AI notes](../skills/chapter25-codex-notes.md).

The [completed split](chapter-split.md) places local/interprocedural SCCP in
24a/24b, incomplete types in 25, construction in 26, escape in 27, and
modules/separate compilation in 28a/28b. The combined old 24/25 snapshots are
retired. Do not backport fixes into those retired copies; their replacements
already retain the constructor, alias, native-exposure and import fixes.

Native library construction and tuning material live in [appendices](appendices/README.md).
Future incremental builds are specified in [incremental-builds.md](incremental-builds.md).

## Four README intentions to review

Keep these four issues visible while reviewing the named chapters. Decisions
may change the construction/escape boundary or later chapter splits.

1. **Construction (active):** final fields should be local SSA values during
   construction, including repeated assignments and loops, with field Stores
   at helper exits. Silent loss of writes is a bug; reject unsupported cases.
   Also decide whether to allow helpers after inlining and defer completion
   checks until SCCP. Those are separate from local field updates.
   The local-value implementation now supports final data fields in construction,
   escape, and modules, including constant defaults and loop Phis. Shared
   function declarations remain fixed. Validate this frontier before adjusting
   the broader constructor rules.
2. **Privacy:** underscore names select the public interface, but sibling access
   to private fields is not yet rejected. Confirm and enforce the intended
   source access rules. The proposed chapter boundary and library changes are
   recorded below; Cliff reviews that proposal before implementation.
3. **Class initialization:** review the corrected rule: A may start, initialize
   B, then resume. Passive references may cycle; active dependencies may not.
   Parents finish before children start. The README now matches this behavior;
   further semantic changes need an explicit decision.
4. **Escape summaries and ELF visibility:** analysis tracks unknown callers,
   while the current ELF writer exports retained named functions. Decide which
   symbols should be public and how that boundary relates to separate compilation.

### Privacy proposal for review

Introduce enforced underscore privacy in **Modules and Lazy Class
Initialization** (`chapter28a`). This is a visible language change:
earlier snapshots allow underscore field access; from Modules onward, access
must obey the declaring class's boundary. The teaching outline already places
privacy here, alongside class names, ownership, and the public interface.
The earlier escape snapshot does not filter fields by underscore spelling.
Its private construction memory is a different concept.

Review follow-up: preserve the distinction between export filtering and access
checks. Before the 2026-10-24 cleanup, the first underscore helper in the
numbered snapshots' tests was Chapter 19's `Chapter19Test.testString`,
`_hashCodeString`. Renaming it to
`hashCodeString` produces the identical LocalSched graph there: single-use
inlining removes it regardless of spelling. In Chapter 25's copy of that test,
the same rename retains an extra function Return. Underscore export filtering
currently starts in Chapter 25 (and its Modules extraction), not Chapter 19.
Old test class names inside newer snapshots do not identify when their current
underscore behavior was introduced. Any access-check change must preserve the
existing ability to hide helpers and classes from the public interface.
Chapter 19's example now uses `hashCodeString` and field `hashCode`; all 42
Chapter19Test tests pass with the existing expected graph unchanged.

Proposed rules:

- A field or method beginning with `_` is accessible only from code in its
  declaring class, including its constructors and method bodies. Enclosing,
  nested, and sibling classes do not gain access to that field. A same-class
  method may access another instance's private fields; the receiver need not
  be exactly `self`.
- A private class name is available in its declaring scope and within the
  class itself. For example, `App` may name its child `_Cache`, but that does
  not grant `App` access to `_Cache._data`. Code outside that declaring scope
  cannot bypass the restriction with a dotted path. Private ancestors also
  keep their descendants out of the public interface. Check both type names
  and class-value references, and distinguish naming a private class from
  using a public value returned by a factory.
- Reads, writes, updates, and method references follow the same rule.
  Preserve the source owner and location when receiver types are unresolved;
  inlining must not change who has access. An unused result or a folded Load
  must not erase an illegal source access.
- Native declarations obey the same source rules. A public wrapper or alias
  may deliberately expose a private native binding; no exception for `sys`.

Source audit:

- `sys/io.smp` calls `libc._exit` twice. Keep the native `_exit` declaration
  private in `sys/libc.smp`, add `val exit = { i32 status -> _exit(status); };`,
  and use `libc.exit` in IO. This preserves the native symbol and behavior.
- Bitset's `_bits` and `_grow`, including `_grow(bit)._bits`, are used within
  the declaring struct and remain legal. Array helpers call their own `_grow`.
  These are the other underscore names in the bundled module library.
- Several retained String spill fixtures use free functions outside `_String`
  to access `_hashCode`. They will need an explicit adaptation in this snapshot
  (for example, making that fixture field public), with any changed allocation
  expectations measured. Do not silently grant file-wide access to pass them.
- The numbered Chapter 25 library has the same `_exit` accesses, and its
  `docs/examples/FileIO.smp` also calls `sys.libc._exit`.

After approval, implement and test in Modules, update its English/Japanese
READMEs with the explicit language boundary, and run the full module suite
including bundled `sys` and native execution. Cover allowed same-class access,
rejected cross-class access, private class lookup, and forward receiver types.
Carry the rule and library adaptations into Chapter 28b, with import tests
preserving the original source owner. Do not backport the new
language restriction into Chapters 10-24 or the named pre-module snapshots.

## Investigate next

Establish an earlier-chapter failure before changing compiler behavior.

### Narrow stores in the ideal evaluator

The prefix-update regression exposed an independent evaluator bug. In Chapter
23, this postfix-only example returns 25710 in Eval2 instead of 110:

```simple
struct C { u8 x=255; val next_noInline={ -> x++; return x; }; };
var c=new C;
val a=c.next_noInline();
val b=c.next_noInline();
return a+10*b+100*c.x;
```

`Eval2.store` copies the raw value into its Java object array. It does not
truncate to the field width, even though Store optimization removes masks that
machine stores perform implicitly. The native prefix-update regression checks
that the stored byte wraps and the expression returns the converted value.
Establish the first affected evaluator and check signed fields and floating
rounding before propagating a fix. Do not change valid generated code to match
an incorrect ideal evaluator.

### Constructor loop with break/continue: worklist assertion

This fails in construction and in the unchanged escape compiler before the
final-field fix, even with a mutable field:

```simple
struct C {
    int x=0;
    new C={int n ->
        for(int i=0;i<n;i++) {
            if(i==1) continue;
            if(i==3) break;
            x+=i+1;
        }
    };
};
return new C(arg).x;
```

`IterPeeps.progressOnList` asserts during Iter with seed 123. The expected value
for arg 3 is 4. This is independent of final-field mutability; retain the
reproducer while reviewing constructor coverage. Scratch probe:
`build/readme-review/mutable_break.smp`.

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

## Future compiler work

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
with separate compilation. Escape analysis now has its own earlier snapshot after
construction; keep its boundary open to review.

### Selective loading of library IR

Reliable incremental rebuilding is separate future work; see the
[incremental build design](incremental-builds.md) for the stale-dependency
example, required input tracking, rebuild propagation, and validation plan.

The Separate Compilation README review found that importing only `sys.io.p`
still loads all ten units in the bundled `sys.o`, including `aryu8`, `aryi64`,
`scan`, and `adt.bitset`. All remain loaded through TypeCheck. Loading only
needed library members is a desired improvement, not current behavior.

Selection works between independent objects: a client of `Counters.o` leaves
an unrelated `Unused.o` unloaded. Once selected, an object's entire saved IR
is reconstructed, and `ParseAll.loadDeps` follows its recorded dependencies.
Splitting the object alone is insufficient while the root's reexports and
unused function bodies still pull in those dependencies. Design member-level
IR loading and dependency discovery together; preserve type identity,
initialization rules, and imported escape summaries. This is separate from
whether imported code is initialized or emitted in the client's object.

## Modules and separate compilation

Class-cycle checking and CLInit runtime lowering belong in 28a/28b. The
[module README](../chapter28a/README.md) explains their semantics; remaining
argument, initialization and pointer plans live in the [roadmap](roadmap.md).
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
