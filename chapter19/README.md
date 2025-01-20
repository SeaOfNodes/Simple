# Chapter 19: Instruction Selection and Portable Compilation

You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter19) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter18...linear-chapter19) it to the previous chapter.


## Portable Compilation

A new abstract `Machine` class is added, one per unique CPU port.  `Machines`
define a name for the port, names for all registers, calling conventions, and
how to generate machine Nodes from *ideal* Nodes.  `Machine` also allows
generating *split* ops and *jumps*, both used by the machine independent
compiler parts during code-gen.


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
