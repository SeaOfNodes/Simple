package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

abstract public class Machine {
    // Human readable machine name.  Something like "x86-64" or "arm" or "risc5"
    public abstract String name();
    // Default opcode size for printing; 4 bytes for most 32-bit risc chips
    public int defaultOpSize() { return 4; }
    // Human-readable name for a register number, e.g. "RAX" or "R0"
    public abstract String[] regs();
    // Create a split op; any register to any register, including stack slots
    public abstract SplitNode split( String kind, byte round, LRG lrg);
    // List of caller-save registers, as a 64bit mask
    public abstract long callerSave();
    // List of never-save registers, e.g. RSP or a ZERO register if you have one
    public abstract long neverSave();
    // Call Argument Mask.  Passed in the function signature and argument
    // number (2-based; 0 is for control and 1 for memory).  Also passed in a 0
    // for the function itself, or for *outgoing* calls, the maximum stack slot
    // given to the incoming function arguments (stack slots reserved for
    // incoming arguments).
    public abstract RegMask callArgMask(TypeFunPtr tfp, int arg, int maxArgSlot);
    // Return register mask, based on signature (GPR vs FPR)
    public abstract RegMask retMask(TypeFunPtr tfp);
    // Return PC register
    public abstract int rpc();
    // Return a MachNode unconditional branch
    public abstract CFGNode jump();
    // Instruction select from ideal nodes
    public abstract Node instSelect( Node n );

    /** Stack Slot Numbering

RA gives all registers a number, starting at 0.  Stack slots continue this
numbering going up from the last register number.  Common register numbers are
0-15 for GPRs, 16-31 for FPRs, 32 for flags, and stack slots starting at
register#33 going up.  These numbers are machine-specific, YMMV, etc; e.g. RPC
is only in stack slot 0 on X86; other cpus start with the rpc in a register
which may spill into any generic spill slot.

For ease of reading and printing, stack slots start again at slot#0 - but
during RA they are actually biased by CPU.MAX_REG.

Stack Layout during Reg Alloc:

         Slot  Value    Description
        ----  -------  -----------
MAXREG+ R+1   argR     maxArgSlot(calleR tfp), can be 0
        ...
MAXREG+ 1     arg0
        ----  -- Caller ------
MAXREG+ 0     RPC
MAXREG+ R+E+N PAD      optional alignment

MAXREG+  +    Spills   Caller Frame Space; N spills
MAXREG+ R+E+1 Spills

MAXREG+ R+E   argE     maxArgSlot(calleE tfp), can be 0
        ...
MAXREG+ R+2   arg0
        ----  -- Callee -------
        ...

Slots defined by maxArgSlot do not have to contain the actual passed argument;
they can be "shadow slots".  If they exist, they are property of the frame /
FunNode where the TFP comes from.

For a given calleR, there are generally many calleE's and they all share the
low-range stack slots.

Example:, Win64, main(int arg) { calloc(1,size); }

                           Post-Warp: SP+56   shadow-main-arg
Pre-alloc               Post-Alloc    SP+48   RPC
--- -------             ----------    -------------
40  spill#3             SP+40         SP+40   spill#3
39  spill#2             SP+32          ""
38  spill#1             SP+24          ""
37  spill#0             SP+16          ""     spill#0
36  shadow-calloc#size  SP+ 8         SP+ 8   shadow-calloc#size
35  shadow-calloc#1     SP+ 0         SP+ 0   shadow-calloc#1
--- --- FRAME BASE ----------         ---- FRAME BASE -------
34  shadow-main-arg     SP+56
33  RPC                 SP+48


    */


    // Maximum stack slot (or 0) for the args in this TFP.  This will include
    // shadow slots if defined in the ABI, even if all arguments are passed in
    // registers.
    public abstract short maxArgSlot(TypeFunPtr tfp);

}
