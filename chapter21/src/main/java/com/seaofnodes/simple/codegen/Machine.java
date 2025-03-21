package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;

abstract public class Machine {
    // Human readable machine name.  Something like "x86-64" or "arm" or "risc5"
    public abstract String name();
    // Default opcode size for printing; 4 bytes for most 32-bit risc chips
    public int defaultOpSize() { return 4; }
    // Create a split op; any register to any register, including stack slots
    public abstract SplitNode split( String kind, byte round, LRG lrg);
    // List of caller-save registers
    public abstract RegMask callerSave();
    // List of callee-save registers
    public abstract RegMask calleeSave();
    // Return a MachNode unconditional branch
    public abstract CFGNode jump();
    // Break an infinite loop
    public abstract IfNode never( CFGNode ctrl );
    // Instruction select from ideal nodes
    public abstract Node instSelect( Node n );

    // Convert a register to a zero-based stack *slot*, or -1.
    // Stack slots are assumed 8 bytes each.
    // Actual stack layout is up to each CPU.
    // X86, with too many args & spills:
    // | CALLER |
    // |  argN  | // slot 1, required by callER
    // +--------+
    // |  RPC   | // slot 0, required by callER
    // | callee | // slot 3, callEE
    // | callee | // slot 2, callEE
    // |  PAD16 |
    // +--------+

    // RISC/ARM, with too many args & spills:
    // | CALLER |
    // |  argN  | // slot 0, required by callER
    // +--------+
    // | callee | // slot 3, callEE: might be RPC
    // | callee | // slot 2, callEE
    // | callee | // slot 1, callEE
    // |  PAD16 |
    // +--------+
    public abstract int stackSlot( int reg );
    // Human-readable name for a register number, e.g. "RAX" or "R0"
    public abstract String reg( int reg );
}
