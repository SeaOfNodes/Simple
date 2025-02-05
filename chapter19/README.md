# Chapter 19: Instruction Selection and Portable Compilation

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter19) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter18...linear-chapter19) it to the previous chapter.


## Portable Compilation

A new abstract `Machine` class is added, one per unique CPU port.  `Machines`
define a name for the port, names for all registers, and how to generate
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

We add a notion of "machine specific" Nodes, which implement the `Mach`
interface.  `Mach` nodes define incoming and outgoing registers for every
instruction, their bit encodings and user representations.  Later we'll add
function unit information for a better local schedule.


## Clean up CodeGen compile driver

More static globals move into the CodeGen object, which is passed around at all
the top level drivers.  Its not passed into the Node constructors, hence not
into the idealize() calls, which remains the majority of cases needing a static
global.  In short the compiler gets a lot more functional, and a big step
towards concurrent or multi-threaded compilation.



## Registers and Register Masks

`Machines` define registers as small dense integers.  The Register Allocator
will use these numbers when allocating.  The mappings from small integers to
machine registers is up to the porter, but there's usally an obvious
one-to-one.  For the X86, RAX will be register 0, RCX register 1 and so on up
to the 16 GPRs.  The XMM registers at 16 and go up to 32.  Register numbers
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
bit V2.  This port supports 16 64-bit GPRs, 32 64-bit XMM registers and the X86
flags.  There is a pattern matcher for matching X86 ops from idealized Simple
Sea-of-Nodes.  Except for the addressing modes pattern matcher matches nearly
one-for-one X86 ops from ideal nodes.

### Normal instruction selection

The CodeGen instSelect phase walks the ideal graph, and maintains a mapping
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