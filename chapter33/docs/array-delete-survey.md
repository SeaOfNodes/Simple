# Deletable-array source survey

2026-11-23. Five production resize implementations, seven source deletion sites.
The compiler now accepts `E[^]`, `new E[^N]`, and `.del()` and requires an ownership
proof at TypeCheck. Runtime recycling remains unimplemented.

| Source | Local deletion check | Full TypeCheck |
|---|---|---|
| [aryu8._grow](../src/main/smp/sys/aryu8.smp) | Grow/add/append, string/integer formatting, and `aryi64.str` compose | Reject: native boundaries in file input, floating formatting, and calendar formatting |
| [aryi64._grow](../src/main/smp/sys/aryi64.smp) | Initializer and repeated `add` calls compose | Pass, including `parseAryI64` |
| [bitset._grow](../src/main/smp/sys/adt/bitset.smp) | Contract propagates through `set` and `tset` | Pass |
| [Timers._add](../src/main/smp/sys/time/Timers.smp) | Consuming recursion not supported | Reject |
| [Canvas.resize](../src/main/smp/sys/draw/Canvas.smp) | Consuming `self.pixels` contract inferred | Pass |

No compiler crashes remain in the final sweep. Initially four cases asserted on
an array `.del` field offset: the receiver became an array only after parsing.
Deferred member resolution now folds that load to the intrinsic. The added
generic receiver signature also exposed an abstract-pointer type-upgrade bug;
abstract struct bounds now remain bounds instead of being looked up as classes.

The source-level poster children in Chapter33Test pass: local arrays, replacement
of a locally constructed owner's buffer, borrowing helpers, list/generator
initializers, and serialized/imported deletion bodies. Saved aliases and
double deletion reject. Extracting an element reference does not retain its
backing array. Incoming ownership becomes a checked function requirement; callers
must discharge it or propagate it in their own contract. A library can therefore
pass TypeCheck while retaining requirements for future callers.

## What the failures tell us

Known native `_exit` calls now retain normal call control but return `TypeMem.TOP`
memory and `TypeScalar.TOP` values. The machine call remains; mixed or unknown
targets remain conservative. This removes `_exit` from the merged memory effects
without adding an exceptional CFG edge.

The earlier reduced case's redundant `Test.$init` guard is handled: loads recover
the initialized flag through whole-memory parameters when every live incoming
path proves it. Variable-sized `[^]` allocations are planned as buddy storage.

Its replacement, `testArrayDeleteAryu8IntegerInit`, reads `sys.integer.MAX_U32`
directly and imports the release library, isolating remote initialization from
`ary.capacity` and `ary.copy`. The saved summary now records the function's own
written aliases, reference-write flag, and escaping identities. Incoming memory
and unrelated functions' escapes are not part of that contract. Effects compose
through calls, including recursive and nonreturning paths. The initializer has
no reference writes. The local proof produces a consuming requirement, and the
fresh-wrapper caller discharges it; the test now passes TypeCheck.

This removes `sys.ary.capacity` and `sys.integer` as blockers in the production
sweep. Allocation contracts now survive serialization too: `ary.copy` returns
its `dst` argument, allocates no storage, and writes no references. The deletion
checker follows returned argument aliases, allowing this copy while still
rejecting uses of an old array through a returned alias after deletion.
`testArrayDeleteAryu8Copy` exercises the actual imported contract without loading
the copy implementation; its fresh-wrapper caller now passes too. Caller checks
use the buffer's entry identity: saved aliases reject, but a reload of the
replaced field is valid. Requirements survive forwarding and serialization.

The repeated-checkpoint restriction is now removed. `testArrayConsumeGrowLoop`
passes, as do nested loops, breaks, fresh allocation/deletion per iteration, and
loops calling an imported forwarder. The backedge check rejects saved array
references and verifies the callee preserves an exclusive owning field. A helper
that publishes an alias of its replacement cannot satisfy that proof.
Copies of the replacement crossing the backedge are conservatively rejected,
even when the next iteration could use and discard them before its next grow.

The two-grow example now passes (`testArrayConsumeTwoGrows`). The checker follows
memory SSA; it substitutes each call's field stores and returned origins at that
call's memory output. A proven unique outgoing field provides a new ownership
version, even on a no-growth path. Loop heads provide iteration-entry versions,
so several grows per iteration do not confuse prior and current buffers. The
initializer-specific proof was replaced by this general call substitution.

String formatting needs both a writable builder and a borrowed input array.
Its unique-buffer requirement excludes an alias in that input parameter; callers
check their actual arguments. Passing the builder's own buffer rejects. Distinct
wrapper parameters are still allowed to alias, so their field paths cannot gain
this disjointness assumption merely by having different names.

## Checkpoint for design review

The latest sweep passes the complete `aryi64`, `bitset`, and `Canvas` migrations.
Within the `aryu8` migration, `_grow`, `add`, `addAry`, `num`, padding, string and
integer formatting, `aryi64.str`, and elapsed-time formatting pass their checks.
The remaining failures are:

| Boundary | Current rejection |
|---|---|
| `sys.io.read` | Native `read` has no usable pointer-memory effect contract |
| Floating formatting | `simple_text_float` returns storage with unknown origin |
| Calendar formatting | `sys.time.local` carries unmodeled native effects |
| Timers | Recursive consuming groups lack allocation/ownership contracts |

The storage graph is still flow-insensitive. It supplies conservative may-stores
and the proof of unique outgoing fields; the deletion checker applies those
facts in memory order. Arbitrary callee overwrites do not yet prove an alias was
cleared. Other conservative limits remain: replacement aliases carried across
iterations, cyclic reference queries, multiple incoming ownership paths, and
array-element ownership slots requiring index proofs. These are useful targets
for the architecture review rather than more library-specific exceptions.

IR format 8 records whether raw/native/initialization effects are absent from
the field graph. Such summaries remain opaque to ownership composition, also
after serialization. Tests cover unsafe saved aliases, helper retention, actual
argument aliasing, multiple grows per iteration, imported forwarders, and an
imported raw-address store. The headless test suite passes. No compiler crashes
occurred in the sweep. Runtime recycling remains unimplemented.

## Reproducing the sweep

[ArrayDeleteSurvey.java](ArrayDeleteSurvey.java) copies `src/main/smp` into a fresh
directory under `build/array-delete-survey`, changes one library per case, then
compiles the whole source `sys`. It does not alter production files or run Simple
code. Each migration marks backing arrays and their allocations `[^]`, saves the
old references, publishes replacements, and calls `.del()` on the old buffers.
For aryi64 it also changes the owning constructor parameter and its factory's
initial allocation; arbitrary caller arrays cannot simply acquire the capability.

From chapter33, compile the runner:

```text
javac -cp build/classes/main -d build/array-delete-survey docs/ArrayDeleteSurvey.java
```

PowerShell or cmd:

```text
java -ea -Xss16m -Xmx2g -XX:ActiveProcessorCount=2 -cp "build/classes/main;build/array-delete-survey" ArrayDeleteSurvey
```

Bash on Unix:

```sh
java -ea -Xss16m -Xmx2g -XX:ActiveProcessorCount=2 -cp 'build/classes/main:build/array-delete-survey' ArrayDeleteSurvey
```

The runner prints the output directory. It contains migrated sources, a
`results.tsv` with every deletion result, and source diagnostics for rejected
compilations. Java exceptions/assertions are recorded separately as crashes.
Adding `-Dsimple.delete.graph=true` also saves the IR and allocation dump for each
case. Quote that option in PowerShell.
