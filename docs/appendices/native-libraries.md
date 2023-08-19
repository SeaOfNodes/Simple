# Native library construction

The complete compiler is [Chapter 28b](../chapter28b/README.md). This appendix
collects the library and native support that accompanied the old combined
Chapter 25. The split preserves that functionality in Chapter 28b.

## Building the library

From `chapter28b`, run:

```sh
make lib release
```

Make builds `build/release/Simple.jar` and, under the selected CPU/ABI directory,
`sys.o` and `simple_crt.o`. The default CPU is x86-64; the default calling
convention is win64 on Windows and SystemV elsewhere. Rebuild serialized
objects after changing the compiler or object format.

`src/main/smp/sys.smp` exposes child units for native libc bindings, I/O,
characters, scanners, arrays, growable buffers, and bitsets. These compile into
one `sys.o`. Clients import its declarations and ideal IR and link calls that
remain out of line. Currently selecting that object loads all its saved IR;
member-selective loading and reliable incremental rebuilding remain future work.

The `simple.sh`, `simple.ps1`, and `simple.bat` launchers set `SIMPLE_HOME` so
the compiler can find the release library. The CLI accepts `-L` for additional
Simple objects or search directories. The chapter README gives an explicit
library/client example that needs no library source in the client root.

## Native symbols and pointer sizes

Native function calls and numeric native data symbols have ELF relocations.
The historical `Chapter25Test.testExternDataEncoding` and `testExternDataImports`
exercise native data; `StaticDataTest` covers addresses
and static images. A data symbol names storage, whereas a function symbol names
callable code; loading the former and calling the latter require different IR.

Simple function pointers currently occupy four bytes. On AMD64, a native
function address names a low-address tail-jump thunk, which can reach native
code outside that range. A weak symbol gives static fields and computed
addresses the same entry across objects; direct calls can still use the native
PLT. This preserves constant initialization of function fields without storing
an eight-byte native address in a four-byte slot. The native linker must place
these Simple entries in the representable address range.

Data pointers still occupy eight bytes. Compact code/data arenas and distinct
full-width native pointer types are [future designs](../roadmap.md#compact-code-and-data-arenas).

## Runtime and examples

`src/main/c/simple_crt.c` supplies the errno accessor and converts native process
arguments to Simple byte arrays for an exported `simple_main` function. This
explicit function entry is distinct from the language's class initializer:
the selected initializer still receives the integer `arg`. General class
initializer argument arrays remain a roadmap item.

The executable examples live in
[`src/test/smp/examples`](../../src/test/smp/examples): Hello World,
Bubble Sort, Capitalize, Dijkstra, and FileIO. The existing Chapter25Test tests
compile and link them through the native harness. `make tests` builds the
library/runtime prerequisites and runs those checks; a Java-only build does
not produce the native prerequisites.

The compiler's in-memory evaluator and the native driver have different entry
interfaces. Use the matching harness when comparing their results, especially
for programs taking an argument array or performing I/O.
