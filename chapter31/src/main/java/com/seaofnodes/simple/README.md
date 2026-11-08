# Compiler source layout

| Directory | Contents |
| --- | --- |
| `parser/` | Recursive-descent grammar routines, lexing, name resolution, and construction of the initial IR. `Var` describes a lexical binding. See the [grammar map](../../../../../../docs/31-grammar.md). |
| `node/` | Sea-of-Nodes IR nodes: graph edges, type computation, and local peephole rewrites. |
| `node/cpus/` | Machine-specific nodes and instruction selection support for `arm`, `riscv`, and `x86_64_v2`. |
| `codegen/` | Compilation driver and passes: module loading, optimization, scheduling, register allocation, serialization, and machine-code emission. `IterPeeps` runs the peephole worklist. |
| `type/` | The type lattice, type interning, and representations of values, structs, functions, and memory. |
| `print/` | IR/assembly printers and adapters for the shared graph viewer. |
| `util/` | General-purpose collections, bit sets, byte buffers, and other small utilities. |

The root package retains `sys.java`, which loads the default Simple namespace.
Java tests, the command-line driver, and the IR evaluator live under
`src/test/java`; the Simple standard library lives under `src/main/smp`.
