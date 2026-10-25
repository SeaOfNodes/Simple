# Incremental builds: future work

Recorded after the Separate Compilation README review, 2026-10-24. This is a
design outline, not a description of implemented incremental builds.

## Current gap

`CompUnit` decides whether to reuse an object when the unit is discovered. It
checks its source timestamp and its dependencies' existing object timestamps.
It does not settle the whole dependency graph before accepting saved IR.

For example:

1. A depends on B and has inlined some of B.
2. B's source changes, leaving B's object unchanged.
3. A client discovers A. A's check sees the old B object and accepts A's object.
4. Later discovery notices that B needs rebuilding, without reconsidering A.

The compiler can therefore reuse code based on stale bodies or constants.
An unchanged public signature is insufficient: Simple imports implementation
details for optimization as well as declarations for type checking.

## Required pieces

- Discover dependencies, mark changed units, and propagate that decision to
  dependents until it settles. Handle cycles as groups. Do this before loading
  IR that may be stale; newly discovered dependencies must also join the plan.
- Record actual inputs. An object can own several source units, so track all
  of their sources and the objects resolved through library search paths.
  The current check assumes `buildDir/name.o`; a missing file has timestamp
  zero and does not trigger rebuilding. Missing inputs must invalidate or
  produce a useful error rather than silently accepting an old result.
- Record compiler and serialization versions, target, ABI, and relevant
  options. Content hashes would also detect changes with preserved timestamps.
  Specify which changes require rebuilding source and which reject an
  incompatible source-free library.
- Make build targets match output ownership. The old ignored `testModule0`
  expects separate A.o and A/B.o, while the current compiler can bundle several
  units into one object. The target must identify the complete set it owns.
- Provide an "ensure current" build operation. Explicit source compilation
  currently always recompiles. An unchanged build should do no compilation or
  linking; a changed object or link input should trigger relinking.
- Publish successful outputs without leaving a partial object accepted after
  failure, and keep the recorded inputs consistent with those outputs.

Initially, rebuilding every dependent of a changed object is a reasonable
conservative rule. Fine-grained interface/body fingerprints can come later.

## Suggested first implementation

Put a small build driver around the compiler with explicit object targets,
recorded inputs, and dependency propagation. Keep individual compilation
requests straightforward. A clean build and an incremental build must produce
the same observable program behavior.

Cover unchanged builds, direct and transitive source edits, inlined constants
and bodies, cyclic dependencies, bundled sources, removed inputs, library-path
changes, compiler/target changes, and failed builds. Use controlled timestamps
or hashes instead of sleeps. Replace the ignored historical experiment with
tests matching the chosen object ownership model.

Selective library IR loading is separate future work. It can reduce compile
cost, but is not required to make conservative incremental rebuilding correct.
