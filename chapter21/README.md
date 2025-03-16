# Chapter 21: Instruction Encoding and ELF


# Table of Contents

1. [Instruction Encodings](#instruction-encodings)
2. [AMD64](#amd64)
3. [ARM](#arm)
4. [RISCV](#riscv)

This chapter will add instruction encodings.
Instruction encoding refers to the representation of 
machine instructions in a specific binary format defined by the 
processor's instruction set architecture (ISA)

After we established instruction selection, we need to encode the instructions before we can write them
into an ELF object file.

Currently, we support these architectures:
### AMD64:
For *x86-64(amd64):*, we use the latest Intel manual for the [encoding rules](https://www.felixcloutier.com/x86/).

### RISC-V:

For *riscv* we currently target [RVA23U64](https://msyksphinz-self.github.io/riscv-isadoc/html/).
- ARM
Each of which has its own instruction encoding format.

To avoid ambiguity, we define the sources  of the information we use to encode the instructions:

### ARMV8/AArch64 :
For *arm*(aarch64) we use this collection for the [encoding rules](https://docsmirror.github.io/A64/2023-06/index.html).
*Note*: We only support 64 bits ARM encodings.

--- 
## Instruction Encodings


## AMD64

#### REX PREFIX
#### MODR/M
#### SIB
#### Displacement
#### Immediate
#### Indirect

=======

## ARM

## RISCV

### Lots of Bits


[^1]: [Intel](smt here)

[^2]: ()
