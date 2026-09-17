# Chapter 25: Modules, Separate Compilation, and SSA Construction

This chapter compiles Simple source files into reusable object files, loads
their types and ideal IR into later compilations, and links native programs
against a separately built system library. It also contains a substantial
parser, type, and memory-SSA redesign to support incomplete information.

These are the contents of the current snapshot. Independent correctness fixes
will move to earlier chapters first; moving the larger SSA redesign backward,
or splitting this chapter, is deferred. See the [backport queue](../docs/chapter-backports.md).

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter25)
in the [linear history](https://github.com/SeaOfNodes/Simple/tree/linear) and
[compare it with Chapter 24](https://github.com/SeaOfNodes/Simple/compare/linear-chapter24...linear-chapter25).

## Compilation units and names

`CompUnit` represents a source or object file. `ParseAll` discovers dependencies,
parses needed sources, and loads previously compiled units. Source and build
trees use corresponding relative paths; class names use dotted paths. For
example, `a/b/foo.smp` supplies the namespace `a.b.foo`.

Each unit has Start/Stop nodes. A function's ownership by a compilation unit is
distinct from an edge representing unknown external callers: retaining code
for emission must not imply that arbitrary callers can reach it.

The driver accepts `--root` for the project root, `-o` for output, and `-L` for
external Simple object search paths, including an object file or a directory.
The compiler also resolves external C symbols for native linking. Names beginning
with `_` control privacy; public reachability depends on enclosing names too.

## Object files contain ideal IR

The ELF writer stores native code and a `.simple` section containing canonical
types, ideal graph nodes, and dependencies. The reader reconstructs that graph
so imported functions can participate in optimization, including inlining.
Calls that remain out of line are resolved by native linking.

`GlobalBits` maps file identities and per-file indices to local dense indices
for aliases, function targets, and return points. Equal local numbers from
independent compilations must not be mistaken for equal identities. Types are
closed and upgraded after loading, including cross-unit cyclic references.

Serialization preserves declaration types separately from sharpened flow types.
Every semantic node/type field needs matching read/write support. The source's
expensive serialization bijection check is currently disabled, and the larger
incremental-rebuild test `testModule0` is ignored. Neither is an active
validation guarantee.

## Constructing SSA before types are complete

Simple still parses directly to SSA without an AST. Forward references mean an
expression's eventual type may be unavailable while parsing it. Syntax and
resolved lexical binding therefore choose topology; types subsequently refine,
optimize, and validate the graph.

- Arithmetic nodes defer integer versus floating-point mode selection, including
  resolution through recursive graph components after SCCP.
- Calls preserve their syntactic receiver slot before the callee kind is settled.
  Symbolic field information defers layout-dependent decisions.
- Loads and ordinary Phis no longer carry parser-supplied lower-bound types.
- `TypeScalar` represents integer, float, memory-pointer, and function-pointer
  values separately from global control/memory bottom.
- `TypeStruct._open` describes an incomplete field set; `_fref` records the
  absence of an authoritative definition. Discovering fields does not define a type.
- Guards carry branch-proven zero/nonzero facts before the input family is known.
  `FunPtrNode` retains an explicit edge to its function's return graph.

The driver runs parsing, pessimistic iteration, SCCP, and final type checking
before serialization and machine selection. Nontrivial inlining is coordinated
with iteration instead of always occurring as an immediate peephole.

## Memory and construction

The parser carries ordinary bulk memory. The optimizer recovers precise aliases
using `MemMerge`, `MemPhi`, and `BulkMemPhi`. A bulk Phi covers all aliases
except its exclusions, represented by parallel precise Phis. Every slice must
be covered exactly once.

User constructors are declared and called as follows:

```java
struct Box {
    i64[] values;
    new Box = { i64[] initial -> values = initial; };
};
return new Box(new i64[1]);
```

An allocation helper is generated at the declaration. It allocates, calls the
hidden `<init>` and then the user constructor, and publishes initialized private
memory through `EscapeNode`s. Parser `Var` metadata detects early field reads
and missing initialization on constructor exits. Store widths come from target
declarations, not from the values being stored.

File-level code is represented by a class initializer (`<clinit>`), distinct
from instance initialization. The older [module design notes](module.md) and
[roadmap](ROADMAP.md) contain proposals and alternatives, especially concerning
initialization order; they are not a specification of all implemented rules.

## System library and examples

The former single `sys.smp` library is organized into units for libc bindings,
I/O, characters, scanners, arrays, growable buffers, and bitsets. The native
driver supports real program arguments. Examples in `docs/examples` include
Bubble Sort, Capitalize, Dijkstra, and FileIO.

`Chapter25Test` exercises library loading and native linking, including a Hello
World call deliberately kept out of line. That test checks the client's external
symbol before linking against `sys.o`. Other tests cover forward constructors,
field updates through calls, literal escapes, and reduced compiler failures.
Independent regressions are candidates for earlier chapters.

## Building and checking

From this directory, `make lib` supplies Java test dependencies and `make tests`
runs the raw chapter suites, standalone suites, system tests, and fuzzer
regressions. `make release` builds the compiler jar and native library artifacts;
`make tags` builds editor tags. Native tools and the selected CPU/ABI must match
the environment; the Makefile currently defaults to x86-64/win64.

Compiler changes can invalidate both serialized IR and native code in `sys.o`.
Rebuild it before interpreting linked-program test results. A source-only subset
is not equivalent to this chapter's full `make tests`.
