# Chapter 28b: Separate Compilation

English | [日本語](README.ja.md)

[Previous: Chapter 28a](../chapter28a/README.md)

The previous chapter compiled every discovered source file together.  This
chapter lets a library and other code compile separately, and have full type
information.  The client needs the library's *object* file, but not its source.
In spirit this is similar to Java's *class* file, with its full execution and
type semantics stored in the file; we happen to be using an ELF file for the
same reason.  The ahead-of-time compiled code is *also* available for the
linking step.

An ordinary native object contains machine code, data, symbols, and
relocations.  Simple adds a `.simple` section with types and the ideal IR.
Importing `.simple` lets the client check types and optimize calls into the
library.  Calls that remain out of line use the library's machine code at
native link time.

## A library and a client

The runnable example has two separate source roots:

```text
src/test/smp/separate/library/Counters.smp
src/test/smp/separate/client/Main.smp
```

`Counters.smp` declares shared state and an instance constructor:

```simple
int calls=0;
val next_noInline = { -> calls++; return calls; };

struct Counter {
    val value=1;
    new Counter = { int n ->
        value=0;
        for( int i=0; i<n; i++ ) value=value*2+1;
    };
    val get = { -> value; };
};
```

`Main.smp` uses both parts of the library:

```simple
val run = { int n ->
    if( !n ) return 0;
    val c = new Counters.Counter(n);
    return c.get()+100*Counters.next_noInline();
};
return run(arg);
```

At runtime, `run(0)` returns without reaching or initializing `Counters`.

For `run(3)`, `Counters` initializes, the constructor computes 7 and
`calls`==1, so we get `(7+100*1)` or 107.

Calling `run(3)`, then `run(5)`, in one process increments the shared `calls`
twice.  The first call gives `(7+100*1)`, or 107; the second gives
`(31+100*2)`, or 231.

(The `_noInline` suffix prevents inlining, so the example exercises native
linking as well as imported IR.)

From this chapter directory, build the compiler and compile the library:

```sh
make lib
make build/classes/test/.ttag
java -ea -cp "build/classes/main:build/classes/test:lib/*" \
  com.seaofnodes.simple.Simple --norun -o build/separate-example/lib/ \
  src/test/smp/separate/library/Counters.smp
```

Then compile and evaluate the client using that object:

```sh
java -ea -cp "build/classes/main:build/classes/test:lib/*" \
  com.seaofnodes.simple.Simple --eval \
  -L build/separate-example/lib/Counters.o \
  src/test/smp/separate/client/Main.smp 3
```


## What crosses the separate compilation boundary

The optimized IR owned by the object is compactly encoded in the `.simple`
section and fully reconstructed when read back in.  The saved state includes
canonical declarations as well as optimized flow types.  E.g. `int x=7` has
declared type `int` and initial value `7`.  Struct field types (both declared
and optimized), function argument types (both declared and optimized), numeric
modes, store widths, and source locations also survive serialization.

Each loaded `.simple` section restores its units into the shared `CompUnit`
tree, mirroring the original code layout.  A dependency shared by several
objects uses the same `CompUnit`.  Loaded aliases and function indices are
renumbered to avoid collisions; all the dense indices end up in the same
namespace.  `GlobalBits` has the mapping from the imported file indices to the
local compilation indices; this remapping extends everywhere through the loaded
code, including recursive types that span several files.  Importing skips
parsing the original source, but it still analysed and type checked with its
new callers.

`ParseAll` is the workhorse that discovers source and object dependencies.  It
tracks missing symbol references, looking them up in the search path one by one,
and loading the matching files.

The end result combines locally parsed and imported IR in one graph, while
keeping their separate `CompUnit`s.  This combined compilation can take
advantage of e.g. constants in the library, or inline library code using the
normal inlining heuristics.  The combined graph goes through the normal
compilation phases, including peepholes, Opto and TypeCheck.

Dependencies between `CompUnit`s are recorded.  An explicitly requested source
file is always compiled.  Other source files are recompiled if their object is
missing, their source is newer, or a recorded dependency's object in the build
directory is newer.  These timestamp checks are not yet a complete incremental
build system, there is some modest future work needed to finish this.

Loading currently works at the object granularity.  Unrelated objects can stay
unloaded, but selecting an object loads its entire saved graph and follows its
recorded dependencies.  Future work will look at loading sub-parts more
incrementally.



## Analysis, ownership, and initialization

An imported function can participate in analysis without belonging to the
client's output.  Loading the `io` library, with its `p` print function, means
that `p` is part of the analysis - but when writing out the `helloWorld` app,
`p` will not end up in the `helloWorld.o` file directly.  It might be inlined
fully, or called out of line, but there won't be a stand-alone copy of it.

After optimization, `Serialize` saves ideal IR before `Lower` expands class
initialization.  `Unlink` then detaches imported bodies for machine compilation
and removes analysis call edges.  The client emits its own bodies and any code
copied into them by inlining; the
remaining calls refer to symbols supplied by the library object.

Class initialization follows the previous chapter's rules.  Importing a type
does not initialize its class.  Active uses retain `CLInit` checks until
lowering (unless optimization clears them out).  The library supplies its
shared class object and flag.  Initialization checks appear at all the active-touch
locations, whether local or remote; including source locations for error
reporting.


## Building and checking

From the repository root:

```sh
make tests CHAPTERS=chapter28b
```

The suite includes the preceding constructor and escape regressions, imported
constructors, initialization cycles across objects, shared static data, native
function thunks, and native library/client execution. Make also builds the
modular `sys` object used by the library tests. Clients can import that object
with `-L`; they do not need to parse the standard library's source again.
`make release` builds the launcher jar and standard library; `make view`
starts the shared graph viewer.

The serialized format is internal and currently unversioned beyond its `C0DE`
magic. Rebuild Simple objects when the compiler or layout changes. The expensive
serialization bijection check remains disabled, and `testModule0`, the older
incremental-rebuild experiment, remains ignored. They are not validation
guarantees for this snapshot.

This completes the seven main snapshots in the [approved split](../chapter-split.md).
See the appendices on [native libraries](../appendices/native-libraries.md)
and [optimizer/backend tuning](../appendices/optimizer-tuning.md).
