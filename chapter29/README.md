# Chapter 29: Tech Debt Cleanup

English | [日本語](README.ja.md)

[Previous: Chapter 28b — Separate Compilation](../chapter28b/README.md)

This chapter makes the compiler easier to read and change. It starts by breaking
the parser into grammar-sized pieces. Further cleanup belongs here; earlier
chapters retain their teaching snapshots.

## Following the grammar

The old `Parser.java` had grown beyond 3,000 lines, mixing recursive descent,
name resolution, scope management, and IR construction. The
[`parser/` package](src/main/java/com/seaofnodes/simple/parser/README.md) separates
those responsibilities without introducing an AST: parsing still builds
Sea-of-Nodes IR directly.

`Parser` owns the source cursor, active scope, type registry, and common token
and IR helpers. Each component shares that state through `Parser P`.
`Statements`, `Expressions`, `Declarations`, `Types`, `Functions`, and `Structs`
follow the corresponding grammar productions. `References` handles name and
field resolution; `Allocations`, `Conversions`, and `ControlFlow` hold shared
graph-building operations. `Lexer` owns tokenization and source locations, and
`Var` records lexical bindings.

The [grammar and code map](docs/29-grammar.md) connects productions to methods.
Start with `Statements.parseStatement` or `Expressions.parseAssignment` and
follow the calls alongside it. Deferred parsing remains explicit: `for` updates
are parsed after their bodies, and declared constructors after field discovery.

The [source layout](src/main/java/com/seaofnodes/simple/README.md) describes the
other packages. `IterPeeps` now lives in `codegen/` beside the compilation passes.

## Checking constructor completion

Instance fields use ordinary Loads and Stores during declaration initialization
and constructor execution. One hidden private-memory value flows through scope
merges and `addReturn`; field values are no longer copied into memory at exit.

Fresh fields start at `TypeScalar.BOT`, which preserves a missing live arm at a
merge. Forward memory types supply the initialization proof at Loads and merged
Returns. Checks run after parsing and an optimistic type solve; there is no
separate initialization walk or field-state map. Class singleton initialization
retains its existing store-at-exit path.

`ConstructorCompletionTest` covers partial assignments, early returns, loops,
declaration defaults and immediate field access, and the formerly disabled
required-number/nullable-default regression. The large comment on
[`Construction`](src/main/java/com/seaofnodes/simple/parser/Construction.java)
explains the type proof and the separate yes/no restriction on references.

The [construction-group experiment](docs/construction-groups.md) adds `C ^arg`
constructor parameters. A new result is incomplete iff an actual argument is
incomplete. A `TypeMemPtr` qualifier propagates this through normal type flow;
a temporary `ConstructNode` types each new result from that call's arguments.
Only self may be read while incomplete; ordinary calls require completed
objects. Field sharpening and inferred requirements are deferred,
with their litmus tests preserved in `ConstructionRequirementsTest`. Separate
compilation of constructors using `^` remains unsupported.

## Further cleanup

Source privacy is deferred. Leading underscores retain the existing export
filtering behavior; this chapter does not yet enforce access restrictions on
source uses. The [roadmap](../docs/roadmap.md) retains that proposal and the other
language candidates.

## Build and test

From the repository root:

```sh
make lib tests release CHAPTERS=chapter29 CTAGS=
```

The suite includes the inherited compiler, evaluator, native execution,
serialization, and separate-compilation tests, plus parser regression tests for
deferred fragments. Historical test class names retain their original identities.
The [Chapter 28b library/client example](../chapter28b/README.md) also runs from
this chapter directory.

IntelliJ has a `chapter29` module and a **chapter29 tests** run configuration.
Build the native runtime and `sys` library with Make before debugging tests
that need them; see [the IntelliJ setup](../docs/intellij.md).
