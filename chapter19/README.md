# Chapter 19: Instruction Selection and Portable Compilation

English | [日本語](README.ja.md)

[Previous: Chapter 18](../chapter18/README.md) |
[Next: Chapter 20](../chapter20/README.md)

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter19) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter18...linear-chapter19) it to the previous chapter.


## Meta-Issues / Code-Dev Issues of Instruction Selection, Register Allocation and Encodings

The output of Instruction Selection - the mapping from idealized Simple Nodes
to machine-specific ones - is difficult to test in bulk without an execution
strategy.  We won't have an execution strategy until all three of Instruction
Selection, Register Allocation and Encodings are done.  Hence in and around the
completion of Encoding we expect to find lots of bugs in Instruction Selection
and Register Allocation.  We certainly hand-inspect the output and fix obvious
problems, but still lots of bugs linger until we can actually run the code.

This means that other chapters have updates and bug-fixes to the work being
described here.


## Portable Compilation

A new abstract `Machine` class is added, one per unique CPU port.  `Machine`
defines a name for the port, names for all registers, and how to generate
machine Nodes from *ideal* Nodes.  `Machine` also allows generating *split* ops
and *jumps*, both used by the machine independent compiler parts during
code-gen.

A new CallingConv enum is added, to allow picking between various calling conventions.

## The Instruction Selection Phase

A new *InstructionSelection* compile phase is added before *Global Code
Motion*.  It is optional, but will replace the *ideal* Nodes with machine-
specific variants.  Instruction selection is called with the target CPU name;
the porting code is looked up and used then.  This allows the Java
implementation at least the ability to add CPU ports lazily.


## Machine Specific Nodes

We add a notion of "machine specific" Nodes, which implement the `MachNode`
interface.  `MachNode` nodes define incoming and outgoing registers for every
instruction, their bit encodings and user representations.  Later we'll add
function unit information for a better local schedule.


## Clean up CodeGen compile driver

More static globals move into the CodeGen object, which is passed around at all
the top level drivers.  It's not passed into the Node constructors, hence not
into the idealize() calls, which remains the majority of cases needing a static
global.  In short the compiler gets a lot more functional, and a big step
towards concurrent or multi-threaded compilation.


## Memory through instruction selection

The parser carries one `$mem` binding. BulkMemPhi splits aliases on demand into
parallel MemPhis, while MemMerge packages a default memory and explicit slices.
Both machine selectors preserve these node classes and their alias information;
they do not turn memory Phis into ordinary value Phis.

This chapter lowers nonzero constructor values into explicit Stores, keeping the
allocation itself a zeroing operation. New consumes `{ctrl, $mem, size}` and
produces `{ptr, $mem}`. Its input is a partial MemMerge covering the allocated
struct's aliases; its single memory result covers those same aliases. The parser
preserves unrelated memory in the surrounding aggregate. The allocation's size
register is input 2, its pointer result is output 0, and memory has no register.

Selected memory operations retain their field alias even when an x86 arithmetic
or comparison instruction absorbs a Load. Global scheduling therefore finds
anti-dependencies through the common MemOpNode, following the relevant slice
through MemMerge. Memory-result instructions are writers; other memory operations
are readers. Trailing ordering edges on writers impose no register requirement.
The simple one-step Load guard used when factoring Phis is unchanged.

Calls still consume and produce whole memory. They terminate their blocks, so
scheduling raises a read before a Call without adding inputs to its argument
list. Direct machine calls embed their constant target and retain only control,
memory, and argument inputs.


## Registers and Register Masks

`Machines` define registers as small dense integers.  The Register Allocator
will use these numbers when allocating.  The mappings from small integers to
machine registers is up to the porter, but there's usually an obvious
one-to-one.  For the X86, RAX will be register 0, RCX register 1 and so on up
to the 16 GPRs. The XMM register numbers start at 16 and go through 31. Register numbers
must be unique; this is how the register allocator tracks them.

A `RegMask` Register Mask class holds onto bitsets of allowed registers.  Most
machine ops are limited to some subset of all registers, and the allowed sets
are written as a RegMask.  Most RegMasks are immutable, e.g. the set of allowed
registers into a `Mul` operation, but the register allocator will make
extensive use of mutable register sets.

Calling conventions dictate many fixed registers, and in practice tend to
really "bind up" the set of allowed registers.


## An X86_64_V2 port, with SystemV or Win64 calling conventions

In the `node/cpus/x86_64_v2` directory is a `x86_64_v2.java` port to an X86 64
V2. This port supports 16 64-bit GPRs, 16 XMM registers and the X86 flags.
There is a pattern matcher for matching X86 ops from idealized Simple
Sea-of-Nodes.  Except for the addressing modes pattern matcher matches nearly
one-for-one X86 ops from ideal nodes.

### Normal instruction selection

The instSelect phase walks the ideal graph, and maintains a mapping
from ideal-to-machine ops.  It does a greedy pattern match, first come first
match.  Ops without any special behavior simply do the local lookup e.g. an
`Mul` exactly matches to an X86 `MulX86` directly.  The RHS of most matches can
be an immediate constant, matching an immediate-flavored version (`MulIX86`).

### Addressing Modes

For many X86 operations, if one of the inputs is a `Load`, the X86 can use an
op-from-memory variant.  There's a specific `address()` call in the X86 matcher
that looks for the various kinds of addressing modes and will build memory-
specific variations.  These all need to be called out specifically, but with a
simple one-line match and one-line allocation of the memory version.

### Compare vs Jmp

In simple, a `BoolNode` produces a `0/1` value.  On an X86 this is done with
the `Cmp/SetXX` opcode sequence.  The X86 `Jxx` takes in `FLAGS` and not a
`0/1`, so the `Jxx` will skip the `SetXX` and directly use the `Cmp`.  Using
the ideal `Bool` directly will require the `SetXX`.  i.e. `return a==3` will
match to a `CmpX86 RAX,#3; SetEQ RAX; ret;` whereas a `if( a==3 )` will match
to a `CmpX86 RAX,#3; jeq;`

##  RISC-V port

In the `node/cpus/riscv` directory is a `riscv.java` port to a RISC-V.
This port supports 32 64-bit GPRs, and 32 floating-point registers.
There is a pattern matcher for matching riscv ops from idealized Simple
Sea-of-Nodes. Currently, we are targeting `RVA23U64`.

### Normal instruction selection

Works the same way as **X86_64_V2**, since riscv doesn't have *complex* addressing modes, greedy
pattern matching doesn't apply to most of the cases. Ops without any special behavior simply do the lookup.

### Restricted ISA
RISC-V is a restricted ISA with simple addressing modes and overloads.
E.g `lea` is not present.


### Branching

In `x86_64_v2`, branching requires a comparison instruction to set flags,
followed by a jump instruction that uses those flags.  In contrast, RISC-V
includes all necessary operands within the branch instruction itself,
eliminating the need for a separate comparison step.

E.g
```
beq x10, x11, some_label
```

`CBranchRISC` implements this behavior.

### No R-Flags

No R-flags exist in RISC-V. To obtain similar functionality:
We can set the value of registers based on the result of the comparison this way explicitly.
```
xor     a0, a0, a1
seqz    a0, a0
```
After the first `xor` - `a0` will be zero if `a0` and `a1` are equal.
The `seqz` instruction will set `a0` to 1 if `a0` is zero from the previous operation.
This matches `SetRISC` in the `nodes/cpus/riscv` port.

### Fixed instruction length

RISC-V instructions have fixed length of 32 bits(in most cases) which means
that we can benefit from displacements in the encoding side.

E.g
```
flw fa5, 0(a0)
flw fa4, 12(a0)
fadd.s fa0, fa5, fa4
```
(No load is needed prior to `flw`)

### ABI names(registers)
In `RISC-V`, general-purpose registers (`GPRs`) range from `x0` to `x31`, and floating-point registers range
from `f0` to `f31`.

To enhance readability and align with calling conventions, RISC-V defines ABI names for registers, providing more intuitive aliases
instead of raw register numbers.



| Register | Alias | Usage                         | Saved By |
|----------|-------|------------------------------|----------|
| x10      | a0    | Function arguments/return values | Caller   |
| x11      | a1    | Function arguments/return values | Caller   |
| x12      | a2    | Function arguments           | Caller   |
| x13      | a3    | Function arguments           | Caller   |
| x14      | a4    | Function arguments           | Caller   |
| x15      | a5    | Function arguments           | Caller   |
| x16      | a6    | Function arguments           | Caller   |
| x17      | a7    | Function arguments           | Caller   |

When passing in arguments to a function, the first 8 arguments from the caller are passed in registers `a0` to `a7`.

### Load floats
Currently, floating-point constants are first loaded into an `integer GPR` before being converted into a floating-point register.
This process is rewritten in the RA stage as:


````
lui a0, 262144
fmv.w.x fa0, a0
````
For non-constant values, we use a broader register mask that
supports both GPRs and FPRs. This implementation can be found in:
`nodes/cpus/riscv/LoadRISC`.





