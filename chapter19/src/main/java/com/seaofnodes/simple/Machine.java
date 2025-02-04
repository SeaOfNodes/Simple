package com.seaofnodes.simple;

import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.node.CFGNode;
import com.seaofnodes.simple.node.IfNode;
import com.seaofnodes.simple.node.Node;

abstract public class Machine {
    // Human readable machine name.  Something like "x86-64" or "win64" or "arm" or "risc5"
    public abstract String name();
    // Human-readable name for a register number, e.g. "RAX" or "R0"
    public abstract String reg( int reg );
    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    public abstract int callInArg( int idx );
//    public abstract  RegMask callInMask( int idx );
    // Create a split op; any register to any register, including stack slots
    public abstract Node split();
    // Return a MachNode unconditional branch
    public abstract CFGNode jump();
    // Break an infinite loop
    public abstract IfNode never( CFGNode ctrl );
    // Instruction select from ideal nodes
    public abstract Node instSelect( Node n );
}
