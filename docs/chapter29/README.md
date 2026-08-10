# Chapter 29: Tech Debt Cleanup

English | [日本語](README.ja.md)

[Previous: Chapter 28b — Separate Compilation](../chapter28b/README.md)

This chapter makes the compiler easier to read and change.  It starts by
breaking the parser into grammar-sized pieces.  Constructors now check field
initialization with ordinary memory types, and can pass an incomplete `self` to
other calls in a type-safe way.

There is lots more cleanup here; earlier chapters are left alone instead of
backporting the cleanups, in part to show the normal progression of large
project development.


## Following the grammar

The old `Parser.java` had grown beyond 3,000 lines, mixing recursive descent,
name resolution, scope management, and IR construction. The
[`parser/` package](../../src/main/java/com/seaofnodes/simple/parser/README.md) separates
those responsibilities without introducing an AST: parsing still builds
Sea-of-Nodes IR directly.

`Parser` owns the source cursor, active scope, type registry, and common token
and IR helpers.  Each component shares that state through `Parser P`.
`Statements`, `Expressions`, `Declarations`, `Types`, `Functions`, and `Structs`
follow the corresponding grammar productions.  `References` handles name and
field resolution; `Allocations`, `Conversions`, and `ControlFlow` hold shared
graph-building operations.  `Lexer` owns tokenization and source locations, and
`Var` records lexical bindings.

The [grammar and code map](29-grammar.md) connects productions to methods.
Start with `Statements.parseStatement` or `Expressions.parseAssignment` and
follow the calls alongside it.  Deferred parsing remains explicit: `for` updates
are parsed after their bodies, and declared constructors after field discovery.

The [source layout](../../src/main/java/com/seaofnodes/simple/README.md) describes the
other packages. `IterPeeps` lives in `codegen/` beside the compilation passes.


## Checking constructor completion

How constructors are handled has gone back and forth over the chapters; this
testament to how implementing a language is a journey of exploration more than
a rote rewriting of the grammer.

Instance fields now use ordinary Loads and Stores throughout initialization.
Instead of keeping field values in locals until construction ends, the
constructor works on a real `self` object with private memory.  This lets it
lend `self` to another constructor or function while construction is underway.

Fresh fields start at `TypeScalar.BOT`, which is the uninitialized indicator.
An initialized integer with an unknown value has `TypeInteger.BOT`, distinct
from the uninitialized `TypeScalar.BOT`, and similarly for other `BOT` types.
Stores replace `TypeScalar.BOT` with that field's type in private memory.
`TypeScalar.BOT` value survives merging at Phis and other control flow, and we
use it to make sure a field is initialized on all paths.

The completion form of `ConstructNode` checks that every required field is
initialized on every constructor exit.  Once the types prove completion and the
node adds no pointer restriction, it folds away during optimization.
Otherwise, `TypeCheck` asks it for an error.  This uses the existing type flow
and error walk; there is no extra construction pass.

Loads check for loading `TypeScalar.BOT` (i.e., loading from uninitialized
memory), and report an error.

Constructors keep their private-memory and precise aliasing, only losing it
after the constructor completes.


## Incomplete Types

A constructor can pass its partially constructed `self` to other constructors
and functions.  This permits doubly linked lists and parent/child tree nodes to
cross-link during construction without reading uninitialized fields.

To pass an incomplete type the receiving parameter must declare the partial
qualifier `^`, for example a`val foo = { Point ^p -> ... }`.  The function may
forward `p`, retain it in another object's private construction memory, and
read fields proven initialized by every caller.  It cannot write through `p`,
even to non-final fields, or store it into an ordinary object or global.  The
original constructor retains its write permission, including writes to final
fields until construction finishes.


```simple
struct _Point {
    int~ x;  // final int x
    int~ y;  // final int y
    new _Point = { int n -> x=n; y=_read(self); };
};
val _read = { _Point ^p -> return p.x; };
return new _Point(arg).y;
```

Here `x` is readable during `_read`, while `y` is still uninitialized.  The
private names keep the callers within this compilation: unknown callers of a
public `^` interface promise no initialized fields.  If any live caller passes
a partial without `x` set, the merged parameter loses that guarantee and the
Load reports an error.

A `new` object is *incomplete* when it starts life and if any of its actual
arguments are incomplete.  With only complete arguments, including null, the
object is complete after construction.  Otherwise it completes with the
original object; the whole collection completes at once.  This allows
construction to assemble a cycle: after the root construction completes, the
reachable objects can be used normally.  Each constructor must still initialize
its own fields before returning.

`TypeMemPtr` carries the *partial* qualifier through ordinary type flow.  An
`InitViewNode` combines `self` with its current private memory to describe which
fields are readable at that point: declared types for initialized fields,
`TypeScalar.BOT` for the rest.  Actual values still come from memory.  Calls
borrowing `self` carry its current Stores on their normal memory edge.

Redundant views fold during optimization.  Other views preserve the partial
restriction or mask unreadable fields until `TypeCheck` checks their consumers
and removes them.  A surviving view is not itself an error: `_read` above needs
only `x`, not a fully initialized object.

These guarantees remain conservative.  Reading a set object-reference field
does not prove that its referent's fields are initialized.  Arrays are already
initialized and cannot contain partial references.  Borrowed reads use aliasable
memory, so allocating another object of the same class is allowed.  Reading an
initialized final field during construction does not freeze its value: the
owning constructor may still change it afterward.

Separate compilation of `^` interfaces remains unsupported.  Inferred field
requirements and nested initialization guarantees remain on the roadmap.

The comment in
[`Construction`](../../src/main/java/com/seaofnodes/simple/parser/Construction.java)
and the [construction notes](construction-groups.md) explain the type flow,
memory edges, and remaining restrictions.


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
that need them; see [the IntelliJ setup](../intellij.md).
