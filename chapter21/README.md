# Chapter 21: Instruction Encoding and ELF

## Meta-Issues / Code-Dev Issues of Instruction Selection, Register Allocation and Encodings

The output of Encodings - conversion of machine Nodes to instruction bits - is
difficult to test in bulk without an execution strategy.  We won't have an
execution strategy until all three of Instruction Selection, Register Allocation
and Encodings are done.  Hence in and around the completion of Encoding we
expect to find lots of bugs in Instruction Selection and Register Allocation.
We certainly hand-inspect the output and fix obvious problems, but still lots
bugs linger until we can actually run the code.

This means that other chapters have updates and bug-fixes to the work being
described here.

Encodings are an entirely bit-picky nit-picky affair.  There's no real rocket
science here, but instead there's an boundless opportunity for off-by-1 errors.
If you find a bug here, do not be surprised!  Its almost surely a "shallow"
bug... and there's bound to be a few until Simple gets more programmer hours
under its belt.



You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter21) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter20...linear-chapter21) it to the previous chapter.

# Table of Contents

1. [Instruction Encodings](#instruction-encodings)
2. [Infrastructure](#infrastructure)
3. [AMD64](#amd64)
   - [REX PREFIX](#rex-prefix)
   - [Large constants](#large-constants)
   - [MODR/M](#modrm)
   - [SIB](#sib)
   - [Displacement](#displacement)
   - [Immediate](#immediate)
   - [Float](#float)
   - [Indirect](#indirectmemop)
   - [Conditional flags](#conditional-flags)
4. [ARM](#arm)
   - [Reg-form](#reg-form)
   - [Shift immediates](#shift-immediates)
        - [asri](#asrimmediate)
        - [LSL(immediate)](#lslimmediate)
        - [LSR(immediate)](#lsrimmediate)
     
   - [Logical Immediates](#logical-immediates)
   - [Float](#float)
   - [Function constant](#function-constant)
   - [Immediate](#immediate)
   - [Conditional flags](#conditional-flags)
   - [Indirect](#indirectmemop-1)
   - [Branching](#branching-)
   - [Rip relative](#rip-relative)
   - [Compare](#compare)

5. [RISCV](#riscv)
   - [Instruction formats](#instruction-formats)
   - [Float](#float)
   - [Indirect](#indirectmemop-2)
   - [Branch](#branch)
   - [Function Constant](#function-constant)
   - [LUI](#lui)
   - [Large constants](#large-constants)

This chapter will add instruction encodings.  Instruction encoding refers to
the representation of machine instructions in a specific binary format defined
by the processor's instruction set architecture (ISA).


After completing instruction selection, the next step is to encode the 
instructions before writing them into an ELF object file.

To avoid ambiguity, we define the sources  of the information we use to encode the instructions:

Currently, we support these architectures:

### AMD64:
For *x86-64(amd64):*, we use the latest Intel manual for the [encoding rules](https://www.felixcloutier.com/x86/).

### RISC-V:

For *riscv* we currently target [RVA23U64](https://msyksphinz-self.github.io/riscv-isadoc/html/).

### ARMV8/AArch64 :
For *arm*(aarch64) we use this collection for the [encoding rules](https://docsmirror.github.io/A64/20a23-06/index.html).
*Note*: We only support 64 bits ARM encodings.

--- 

## Instruction Encodings


## Infrastructure

The instruction selection phase creates the machine friendly nodes from ideal
nodes.  These machine friendly nodes define an *encoding* function that is
called from the Encoding driver found in CodeGen.

```java
for( CFGNode bb : _code._cfg )
    for( Node n : bb._outputs )
        if( n instanceof MachNode mach ) {
            int off = _bits.size();
            mach.encoding(this);
            _opLen[n._nid] = (byte)(_bits.size()-off);
    }
```

Based on the selected target, we either need to look out for little endian or
big endian encodings.  These are being handled by `add4` which adds to the bit
stream in the correct order.  As of now the bitstream is a simple
ByteArrayOutputStream structure.

```java
@Override public void encoding( Encoding enc ) {
    enc.add4(0); // adds 4 bytes to the  bitstream
}
```

On *RISC-V*, where the documentation provides big-endian encodings, this
function also converts them to little-endian.  For other architectures, no
conversion is necessary.

## Endianness

ARM, RISC-V, and x86 are all little-endian architectures in practice, meaning
the least significant byte is stored first. We ensure that instruction
encodings are generated in little-endian format accordingly.

```java 
// Little endian write of a 32b opcode
public void add4( int op ) {
    _bits.write(op    );
    _bits.write(op>> 8);
    _bits.write(op>>16);
    _bits.write(op>>24);
}
```
For example, consider a 32-bit encoding for fixed-width instructions:
*10110101100111101011001001101101*.

We append the least significant byte first, then the next byte, and so on.
``` 
_bits.write(op    );
```

`_bits.write()` only appends the least significant byte.  By shifting the value
to the right, we can bring the next byte into the least significant position,
allowing us to write each byte in sequence.


## AMD64 X86-64

As opposed to riscv arhitectures, where the instruction width is fixed,
*x86-64* has variable width instructions.  This is common with *CISC (Complex
Instruction Set Computer)* architectures, where the instruction width can vary
from 1 to 15 bytes.  Since AMD64 supports many indirect addressing modes, the
goal with *CISC* in general is to complete a task in as few lines of assembly
as possible.

### REX PREFIX

Since we are targeting the 64-bit version of x86, we need to handle this
prefix.  In 32-bit mode, however, it is typically unnecessary.

Generally speaking the *REX* prefix must be encoded when:
 - using one of the extended registers (R8 to R15, XMM8 to XMM15, YMM8 to YMM15, CR8 to CR15 and DR8 to DR15);
 - using 64-bit operand size and the instruction does not default to 64-bit operand size(most ALU ops)
 - using a 8-bit operand with RSI, RDI, RBP

A *REX* prefix must not be encoded when:
 - using one of the high byte registers AH, CH, BH or DH (not done currently in Simple)

*Note:* When encoding SSE instruction, the *REX* prefix (0x40) must come after
the `SSE` prefix.  In all other cases, it is ignored.

In Simple, the REX prefix is just appended to the beginning of the bit stream
(except SEE float instructions).  The layout is the following:

| **Field** | **Length** | **Description**                                                                 |
|:---------:|:----------:|---------------------------------------------------------------------------------|
| `0100`    | 4 bits     | Fixed bit pattern                                                              |
| `W`       | 1 bit      | When 1, a 64-bit operand size is used. Otherwise, 32-bit operand size is used. |
| `R`       | 1 bit      | Extension to the **MODRM.reg** field.                                          |
| `X`       | 1 bit      | Extension to the **SIB.index** field.                                          |
| `B`       | 1 bit      | Extension to the **MODRM.rm** or **SIB.base** field.                           |


```java 
    public static int REX_W  = 0x48;
``` 

```java
public static int rex(int reg, int ptr, int idx, boolean wide) {
    // assuming 64 bit by default so: 0100 1000
    int rex = wide ? REX_W : REX;
    if( 8 <= reg && reg <= 15 ) rex |= 0b00000100; // REX.R
    if( 8 <= ptr && ptr <= 15 ) rex |= 0b00000001; // REX.B
    if( 8 <= idx && idx <= 15 ) rex |= 0b00000010; // REX.X
    return rex;
}

    ... 
    enc.add1(x86_64_v2.rex(dst, src, 0));
```

Setting THE `W` bit gives us:
`0b01001000 = 0x48;`

#### Opcode
In our case, the opcode is a 1 byte field that specifies the operation to be performed.
```
enc.add1(opcode()); // opcode 
```

#### MODR/M

| **Field**     | **Length** | **Description**                                                                                                                                                                                                                                                                                                                                                                                                  |
|:-------------:|:----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **MODRM.mod** | 2 bits     | In general, when this field is `b11`, then register-direct addressing mode is used; otherwise register-indirect addressing mode is used.                                                  
| **MODRM.reg** | 3 bits     | This field can have one of two values:<br><br>• A 3-bit opcode extension, which is used by some instructions but has no further meaning other than distinguishing the instruction from other instructions.<br><br>• A 3-bit register reference, which can be used as the source or the destination of an instruction (depending on the instruction). The referenced register depends on the operand-size of the instruction and the instruction itself. The `REX.R`, `VEX.~R` or `XOP.~R` field can extend this field with 1 most-significant bit to 4 bits total. |
| **MODRM.rm**  | 3 bits     | Specifies a direct or indirect register operand, optionally with a displacement. The `REX.B`, `VEX.~B` or `XOP.~B` field can extend this field with 1 most-significant bit to 4 bits total.                                                                                                                                                                                                                     |


We deal with the MODR/M byte in a similar way:

The first thing we need for the modrm byte is the 2 bits mod.

##### mod

The layout for mod is the following:
```
public enum MOD {
    INDIRECT,       // [mem]
    INDIRECT_disp8, // [mem + 0x12]
    INDIRECT_disp32,// [mem + 0x12345678]
    DIRECT,         //  mem
};
```

##### reg
The reg field is a 3 bit field capable of encoding 15 different registers.

##### r/m

The rm field specifies a direct or indirect register operand and is capable of encoding 15 different registers.


```java
public static int modrm(MOD mod, int reg, int m_r) {
    // combine all the bits
    return (mod.ordinal() << 6) | ((reg & 0x07) << 3) | m_r & 0x07;
}
```

The `modrm` function is then used to build up the `ModR/M` byte.
Usually this follows after the opcode.

``` 
enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
```

Encoding a reg form might look like this:
```      
short dst = enc.reg(this ); // src1
short src = enc.reg(in(2)); // src2
enc.add1(x86_64_v2.rex(dst, src, 0));
enc.add1(opcode()); // opcode
enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
```

#### SIB
The SIB byte has the following fields:

| **Field**     | **Length** | **Description**                                                                                                                                                                                                                                            |
|:-------------:|:----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **SIB.scale** | 2 bits     | This field indicates the scaling factor of `SIB.index`, where **s** (as used in the tables) equals 2^SIB.scale^.                                                                                                                                           |
| **SIB.index** | 3 bits     | The index register to use. See Registers for the values to use for each of the registers. The `REX.X`, `VEX.~X` or `XOP.~X` field can extend this field with 1 most-significant bit to 4 bits total.                                                    |
| **SIB.base**  | 3 bits     | The base register to use. See Registers for the values to use for each of the registers. The `REX.B`, `VEX.~B` or `XOP.~B` field can extend this field with 1 most-significant bit to 4 bits total.                                                     |

It has the same layout as the MODR/M byte.
```java 
public static int sib(int scale, int index, int base) {
    return (scale << 6) | ((index & 0x07) << 3) | base & 0x07;
}
```

```java
enc.add1(x86_64_v2.sib(_scale, idx, x86_64_v2.RBP));
```

#### Displacement
Displacement can happen in 2 forms:
```java
public enum MOD {
INDIRECT, //  [mem]
INDIRECT_disp8, // [mem + 0x12]
INDIRECT_disp32,// [mem + 0x12345678]
DIRECT,          // mem
};
```

```java
if( mod == MOD.INDIRECT_disp8 ) {
    enc.add1(offset);
} else if( mod == MOD.INDIRECT_disp32 ) {
    enc.add4(offset);
}
```
The displacement goes before the immediate, so for example:
``` 
sub [eax + 2], 4
```
``` 
bytes.write(0x83);
bytes.write(modrm(INDIRECT8, 5, 0 /* eax */));
bytes.write(2); // displcement goes first
bytes.write(4);
```

#### Immediate
The immediate byte/bytes go after the `ModR/M` byte, we are capable of encoding 4 bytes of immediates depending on the need.
```java
// immediate(4 bytes) 32 bits or (1 byte)8 bits
if( x86_64_v2.imm8(_imm) ) enc.add1(_imm);
else                       enc.add4(_imm);
```

####  INDIRECT(MemOp)
X86-64 support multiple indirect addressing modes:


#### Float
For float operations we use `SEE SIMD` instructions that operate on scalar single precision floating0-point values located in the
XMM registers or memory.
*Note*: When using XMM registers for ALU operations, the encoding is really similar with the exception that we need
to subtract the `XMM_OFFSET` from the register number.
We count the registers continuously, so we need to subtract the offset to get the GPR register pair that corresponds to the XMM register.

```java 
short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
```

```java
public static int XMM_OFFSET = 16; 
```

| **X.Reg** | **8-bit GP** | **16-bit GP** | **32-bit GP** | **64-bit GP** | **80-bit x87** | **64-bit MMX** | **128-bit XMM** | **256-bit YMM** | **16-bit Segment** | **32-bit Control** | **32-bit Debug** |
|:--------:|:------------:|:-------------:|:-------------:|:-------------:|:-------------:|:-------------:|:--------------:|:--------------:|:-----------------:|:-----------------:|:---------------:|
| `0.000 (0)` | AL | AX | EAX | RAX | ST0 | MMX0 | XMM0 | YMM0 | ES | CR0 | DR0 |

We see that `XMM0` corresponds to `RAX` and so on.

#### Large constants

##### INT: We just use `MOV r64, imm64` instruction form.

##### FLOAT: We use relocation and use constant from memory.

#### Function constant
To load a function constant we use lea which will load the address into the specified reg
relative to the instruction pointer.
`lea rax, [rip+disp32]`

#### Conditional flags:
The FLAGS register is the status register that contains the current state of an x86 CPU.

Let's consider a simple example that presents all the instructions that we need to encode for
conditional code.

```
int square(int a) {
    // cmp    DWORD PTR [rbp-0x8],0x1
    // `sete   al`
    bool da = a == 1;
    
    //  cmp    eax,0x1 
    if(da == 1) {
        return 1;
    }
    return 2;
}
```

First we need a compare instruction comparing `a` with 1.
```
cmp    DWORD PTR [rbp-0x8],0x1
```

This compare instruction then sets the conditional flags:
>  The CF, OF, SF, ZF, AF, and PF flags are set according to the result.

We then use the `sete` instruction to save out the result of the comparison to a register:

`sete   al`

For the if condition we do an extra comparison based on the result of the first bool.
```
 cmp    eax,0x1 
```

And then a conditional branch that is relying on the flags set by the comparison(`a == 1`).
```
jne    1159 <square(int)+0x29>
``` 


We'll see how this differs in ARM and RISCV.

---


## ARM
Arm instructions are all 32 bits wide, and are always little-endian.
Arm is a RISC architecture, it prioritizes "register-to-register" forms.

#### Reg-form
We encode the regs forms as [shifted registers](https://docsmirror.github.io/A64/2023-06/orr_log_shift.html).
```
orr reg1, reg2
```
```
x0 = x0 | (x1 << 0)
```
The same thing applies for all the other reg forms.
Because of this similarity, we can lift the encoding of the reg form to a common function.

```java  
short self = enc.reg(n);
short reg1 = enc.reg(n.in(1));
short reg2 = enc.reg(n.in(2));
int body = r_reg(opcode, 0, reg2, 0,  reg1, self);
enc.add4(body);
```
*imm6* and
*shift*
are irelevant for the reg form, so we just set them to 0.

### Shift immediates
This refers to: *asri, lsli, lsri*
Encode to shift immediates if the shift amount is between [0, 63] inclusive.

#### ASR(immediate)
For arithmetic Shift Right immediates, we set *immr* to the immediate we wish to encode, and imms to `63` = `111111`.
This is following the rules of [asri encoding](https://docsmirror.github.io/A64/2023-06/asr_sbfm.html).

The `sf` bit is included in the opcode, `N` is implicitly added to the bitstream after the execution of the 
`imm_shift` function.
```java 
enc.add4(arm.imm_shift(0b100100110,_imm, 0b111111, rn, rd));
```

#### LSL(immediate)

```java 
// UBFM <Xd>, <Xn>, #(-<shift> MOD 64), #(63-<shift>)
// immr must be (-<shift> MOD 64) = 64 - shift
enc.add4(arm.imm_shift(0b110100110, 64 - _imm, (64 - _imm) - 1, rn, rd));
```

We know that `_imm` is never a negative number and is between 0 and 63, so we can safely use `64 - _imm` as the `immr` value.
We take away this form of the immediate from 64 to invert the bits.

For *imms* - the following condition must hold *imms + 1 = immr*;
After subbing in the values we get:

> imms + 1 = 64 - _imm;

> imms = (64 - _imm) - 1;

Hence, the `imms` value is `(64 - _imm) - 1`.

#### LSR(immediate)
Same as *ASR*.


#### Logical Immediates 
Logical immediates refer to *andi, xori, orri*.

```java 
// Can we encode this in ARM's 12-bit LOGICAL immediate form?
// Some combination of shifted bit-masks.
private static int imm12Logical(TypeInteger ti) {
    if( !ti.isConstant() ) return -1;
    if( !ti.isConstant() ) return -1;
    long val = ti.value();
    if (val == 0 || val == -1) return -1; // Special cases are not allowed
    int immr = 0;
    // Rotate until we have 0[...]1
    while (val < 0 || (val & 1)==0) {
        val = (val >>> 63) | (val << 1);
        immr++;
    }
    int size = 32;
    long pattern = val;
    // Is upper half of pattern the same as the lower?
    while ((pattern & ((1L<<size)-1)) == (pattern >> size)) {
        // Then only take one half
        pattern >>= size;
        size >>= 1;
    }
    size <<= 1;
    int imms = Long.bitCount(pattern);
    // Pattern should now be zeros followed by ones 0000011111
    if (pattern != (1L<<imms)-1) return -1;
    imms--;
    if (size == 64) return 0x1000 | immr << 6 | imms;
    return (32-size)<<1 | immr << 6 | imms;
}
```

imm12Logical returns the 12-bit immediate encoding for logical operations if it is possible to encode it, otherwise -1.
```java
int imm12;
return and.in(2) instanceof ConstantNode off && 
off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
? new AndIARM(and, imm12) 
```
```java 
arm.imm_inst(enc,this,0b100100100,_imm); 
```

#### Float
Similarly to x86, we have to subtract the offset to get the GPR register pair that corresponds to the *D* register.
``` 
short dst = (short)(enc.reg(this ) - arm.D_OFFSET);
```
For loading float constants we use the `ldr(literal)` instruction.
The offset is added to the PC register to get the address of the literal.

```java 
// Any number that can be expressed as +/-n * 2-r,where n and r are integers, 16 <= n <= 31, 0 <= r <= 7.
int body = arm.load_pc(0b01011100, 0, dst);
```

#### Large constants

##### INT: We can use different combinations of movs(movz, movk, movn) - this allows us to avoid PC relative loading.

##### FLOAT: We use the *LDR (immediate, SIMD&FP)* instruction relative to the PC


#### Function constant 
To load a function constant we store the current PC into x0 and use that to access the constant.
Generally, we try to avoid load as adding is faster.
```
adrp    x0, 0
add     x0, x0, 0
```

#### Conditional flags
In arm we set the conditional flags after a comparison -
this works similarly to x86.
 
```
int square(int a) {
// subs
// cset	w8, eq  // eq = none
bool da = a == 1;
    // bne
    if(da == 1) {
        return 1;
    }
    return 2;
}
```
In arm comparisons are done with `subss` which does a subtraction between the two
operands and also sets the flags.
`CSET` is used to look up the flags and save it out to a register.

Later on `b.ne` relies on this flag again when it attempts to conditionally execute a branch.

#### INDIRECT(MemOp)
Arm supports the following indirect forms:

[Load](https://docsmirror.github.io/A64/2023-06/ldr_reg_gen.html): `ldr dst, [base + index]` where both, base and index are registers.

[Load](https://docsmirror.github.io/A64/2023-06/ldr_imm_gen.html): `ldr dst, [base + #offset]` where base is a register and the offset is an immediate.

[Store](https://docsmirror.github.io/A64/2023-06/str_reg_gen.html): `str dst, [base + index]`

[Store](https://docsmirror.github.io/A64/2023-06/strb_imm.html): `str dst, [base + #offset]`

If the offset is not a con
#### Branching
`B.cond` - branch [conditionally](https://developer.arm.com/documentation/dui0802/b/A32-and-T32-Instructions/Condition-codes?lang=en) to a label at a PC-relative offset.

> cond = is one of the standard conditions.

---

## RISCV
RISCV instructions are all 32 bits wide, and are always little-endian.
RISCV is a RISC architecture, it prioritizes "register-to-register" forms.

#### Instruction formats

##### R-TYPE
This encoding format is used for reg-to-reg operations.
Such as `AddRISC`, `MulRISC` etc.
```java 
public static int r_type(int opcode, int rd, int func3, int rs1, int rs2, int func7) {
     return (func7 << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
}
```

##### I-TYPE
This layout is used for immediate formats. Such as `AddIRISC`

```java 
 public static int i_type(int opcode, int rd, int func3, int rs1, int imm12) {
     assert opcode >= 0 && rd >=0 && func3 >=0 && rs1 >=0 && imm12 >= 0; // Zero-extend by caller
     return  (imm12 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
 }
```

##### S-TYPE

This encoding format is used for `StoreRISC`.

```java
public static int s_type(int opcode, int func3, int rs1, int rs2, int imm12) {
  assert imm12 >= 0;      // Masked to high zero bits by caller
  int imm_lo = imm12 & 0x1F;
  int imm_hi = imm12 >> 5;
  return (imm_hi << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm_lo << 7) | opcode;
}
```

##### B-TYPE

This encoding layout is used by `BranchRISC`.
```java
 // immf = first imm
 // immd = second imm
 // BRANCH
public static int b_type(int opcode, int immf, int func3, int rs1, int rs2, int immd) {
     return (immd << 25 ) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (immf << 7) | opcode;
 }
```

##### U-TYPE
This layout is used for `AUIPC` and `LUI`(upper immediate is 20-bits).

```java 
 public static int u_type(int opcode, int rd, int imm20) {
     return (imm20 << 12) | (rd << 7) | opcode;
 }
```

##### J-TYPE
Used for unconditional jumps. (`UJmpRISC`)
```java 
 public static int j_type(int opcode, int rd, int imm20) {
     return imm20 << 12 | rd << 7 | opcode;
 }
```

#### FLOAT
For loading float constants, we use indirect memory load(PC-relative).
``` 
AUIPC dst,#hi20_constant_pool
Load dst,[dst+#low12_constant_pool]
```

#### INDIRECT(MemOp)

[Load](https://msyksphinz-self.github.io/riscv-isadoc/html/rvi.html#lw): `lw rd,offset(rs1)`

[Store](https://msyksphinz-self.github.io/riscv-isadoc/html/rvi.html#sw): `sw rs2,offset(rs1)`

#### BRANCH
RISC-V is unusual because branch instructions include the comparison and branch target in one instruction.

#### Immediates
We only encode the imm form for the *ALU* operations if they fit into 12 bits (signed).
Otherwise we do a Load Upper Immediate (high 20 bits) or both.

```
addiw	a5,a5,123
```
vs
(do it with extra load):
```
addw a5,a5,a4
```

### FLOAT Constants
- Stored in the **constant pool**.
- Accessed via **PC-relative loads**.

### INT Constants
- **12-bit constants**
   - Use `ADDI`
- **20-bit constants**
   - Use `LUI`
- **32-bit constants**
   - Use `LUI` + `ADDI` combo
   - 

#### Function Constant
In our RISC-V code, when we need to load a function-local constant, 
we store the current PC into a register and use that as a base to access the constant. However, we usually try to avoid loads, since simple arithmetic like addition is faster and more efficient than accessing memory.

```java 
// auipc  t0,0
// addi   t1,t0 + #0
```



## Relocation

Relocation allows code to be *relocated* to a new code offset.  Code often
refers to other code and data; `call` instructions target subroutines,
`branches` target other instructions, large constants are often loaded from a
*constant pool*.

### Local Relocation

Local relocation patches up local, or self-referential, code; the most common
form is branch targets.  When writing instructions into the `BAOS` (bit stream)
forwards branches towards future instructions have an unknown offset - the
offset depends on the size of the encodings in between the branch and target.
For `riscv` and `arm` targets this sounds easy (and instructions are 4 bytes,
so just count instructions)... except that some encodings use multiple
instructions so are not actually 4 bytes long.  For X86's wild psuedo-random
instruction lengths the act of figuring out the opcode length is just as hard
as writing the opcode in the first place.

Alson X86 adds another wrinkle: short and long form branches.  If a branch
target lies within a signed byte range (+/-127) X86 will use a 2 byte branch
encoding, otherwise a 6 byte encoding.  Simple includes a pass to compact these
branches and use 2 bytes wherever possible - but this pass has to slide code
around to make space for the extra 4 bytes as needed, which changes all the
other branch offsets.

Once the code is finally settled into "shape", a local relocation pass is made
to update all the self-references.  Basically, the branch offsets in the byte
array get overwritten with the actual values.

At the end of this pass, the code is locally correct at some offset (generally
0), but this is not the end - code is often *linked* against other code
generated in other compilations, and again various offsets will need to be
adjusted.

### Global Relocation

Code in one compilation unit wants to call code in another... this requires a
*global relocation*.  A common example is a *loader* loading an *ELF* file,
which mostly happens every time a new program is run, and requires the
relocation information to be stored to disk (in e.g. the ELF file).  JIT
systems will neeed to do this as well across different compilations, although
the relocation information can stay in memory.

#### Getting Relocations to All Agree

Since the relocations can be written to disk, the disk format, loader and "code
shape" all have to agree.  Suppose we want to call from one compilation unit to
another - e.g. the program is calling `malloc` from `libc`.  The caller is a
Simple using a machine-specific version of generic `CallNode` with the target
being C-compiled code.  During loading, the placement of the `call` and `libc`
is determined by the loader; the two different blocks of code are written into
memory in some order (generally with lots of other blocks of code).  Now we
need to patch the `call`'s target to be `malloc` in the `libc` block of code.

For an X86 external call, the encoding is a 5-byte instruction, one byte of
opcode and 4 bytes of PC-relative target address.  The needed patch is the
delta between the X86-specific `call` instruction *end* and the start of
`malloc`, and is written directly on the last 4 bytes of the `call`.  The
encoding type in the ELF file has to describe this.

For a RISCV external call, the encoding is 2 4-byte instructions each carrying
some of the bits.  The `LUI` loads the target upper 20 bits, and then the
`JALR` adds in the lower 12 bits (32-bit absolute target range).  The `LUI`
might be replaced with a `AUIPC` to get a 32-bit pc-relative range.  Patching
here updates 20bits in one instruction and 12bits in another.  Again the ELF
file encoding type has to describe this; using a X86-style patch here will just
crush the wrong bits and make a broken program.

#### Large Constants

Large constants are those which cannot be easily directly loaded into a
register, and instead are loaded from memory.  For security reasons they are
often kept in a seperate address space with e.g. *read-only* permissions instead
of *execute* permissions.  This means their final address, relative to the code,
varies according to placement - same as for calls crossing compilation units.

Also large constants can be shared across compilation units (one can imagine
repeated uses of `pi` in HPC codes); sharing reduces cache footprint.  In any
case, one the locations of the code and constants are known, the code needs to
be patched to load at the correct offset.  Like the `call` example above, the
load instruction variant and patch variant all need to agree.  An X86 might use
either a 4-byte address or an 8-byte address depending on X86 variant; the
RISCV probably uses some variant of an `LUI` and a `Load`, with the immediate
split into 20/12 bits.  Also the RISCV probably wants to "share" the constant
pool 20 bits in the same `LUI`, but have different loads for different
constants coming out of the same pool.  ARM will have yet another variant.

