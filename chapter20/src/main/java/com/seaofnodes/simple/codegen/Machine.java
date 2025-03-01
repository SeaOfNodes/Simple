package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;

abstract public class Machine {
    // Human readable machine name.  Something like "x86-64" or "arm" or "risc5"
    public abstract String name();
    // Human-readable name for a register number, e.g. "RAX" or "R0"
    public abstract String reg( int reg );
    // Create a split op; any register to any register, including stack slots
    public abstract SplitNode split( String kind, byte round, LRG lrg);
    // Return a MachNode unconditional branch
    public abstract CFGNode jump();
    // Break an infinite loop
    public abstract IfNode never( CFGNode ctrl );
    // Instruction select from ideal nodes
    public abstract Node instSelect( Node n );
}
