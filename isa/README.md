# Shared instruction encoders

This module contains the x86-64, AArch64, and RISC-V instruction encoders used
by Chapters 21-25, plus their ARM/RISC-V test evaluators.
It is compiler implementation code, shared because the same concrete instruction
should have the same encoding in every chapter. It requires only the JDK.

The compiler still decides which instruction to select, which physical registers
to use, what an operand's width and extension mean, and where blocks, stack
slots, constants, and relocation targets belong. The shared encoder receives
those decisions as numbers and writes bytes. It imports no chapter classes and
has no knowledge of nodes, types, calling conventions, or compilation phases.

## Follow one instruction

Chapter 21's `AddX86` selects opcode `0x03` and retains its register masks and
two-address constraint. Its `RegX86` base obtains the allocated registers:

```java
@Override public void encoding(Encoding enc) {
    X86.reg(enc,opcode(),enc.reg(in(1)),enc.reg(in(2)));
}
```

The shared `X86.reg` emits REX.W, the opcode, and ModRM. For `add r9,r10`, those
bytes are `4d 03 ca`: REX extends both register fields, `03` selects ADD with the
destination in ModRM.reg, and `ca` selects register operands 1 and 2. Chapter 21
records the resulting instruction offset and length, just as before.

`Encoding` implements the small `CodeSink` interface using its existing `add1`,
`add2`, `add4`, and `add8` methods. Shared functions append directly to the compiler's
buffer; they do not allocate temporary instruction objects or byte arrays.

## Current boundary

`X86` owns prefixes and addresses, integer and scalar floating-point operations,
loads/stores, comparisons, constant materialization, register/stack copies,
calls, returns, and branch bytes. The chapter chooses branch targets and runs
relaxation; shared helpers calculate instruction lengths and patch displacements.

Registers are hardware indices 0-15 in the explicitly selected GPR or XMM bank.
Memory operands take a base, an optional index (`-1` for absent), a displacement,
and a scale encoded as 0-3. LEA also supports an absent base. Sizes are log2 of
the byte width, 0-3. The chapter converts its own types and register numbering
to these concrete operands; the encoder never examines compiler types.

`Arm64` and `RiscV` own opcode constants and instruction-field packing. ARM's
logical-immediate encoding and MOVZ/MOVN/MOVK constant construction are shared
too. Their chapter wrappers resolve node operands and register banks, then
append the resulting instruction words. Instruction selection, masks, scheduling,
frame layout, relocations, constant placement, and object-file writing remain
chapter-local.

`src/test-support` contains one `EvalArm64` and one `EvalRisc5`. The chapter test
harness still builds the image, links it, sets arguments and checks results.
These small interpreters implement the tutorial's instruction subset, not full
system emulation. Their default test-runtime convention recognizes `calloc` at
address -4 and `write` at -8; an overload accepts different sentinel addresses.
They import no compiler classes and are compiled only with tests.

## Tests and builds

`make -C isa tests` runs byte-exact tests without building any compiler. Expected
bytes cover register extensions, low-byte register selection, signed loads,
immediate/displacement boundaries, SIB cases, calls, branches, and prefix order.
ARM/RISC-V checks use fixed instruction words, offset boundaries, and small
evaluator programs, including ARM constants that need several instructions.
The chapter suites continue to test compiler wiring and execute generated code.
The root `make tests` runs both shared and chapter tests.

Chapter 21-25 Makefiles include `isa.mk`; their Maven builds explicitly add the
shared main and test-support source directories. Earlier chapters do not compile
ISA sources. The linearized Maven build adds them when it reaches Chapter 21.
`isa.iml` describes the IDEA encoder module; `src/test-support/isa-eval.iml`
describes the evaluators, which chapter modules depend on in test scope.
Linearized checkouts carry `isa/` beside the other shared modules. Release jars
include the compiled encoders. Make's existing release recipe also bundles the
chapter tests and their shared evaluators; Maven's ordinary main jar excludes
test classes.
