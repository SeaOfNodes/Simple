package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.MachNode;

// Live Range
public class LRG {

    // Dense live range numbers
    final int _lrg;

    // A sample MachNode in the live range
    MachNode _mach;

    // AND of all masks involved; null if none have been applied yet
    RegMask _mask;

    LRG( int lrg ) { _lrg = lrg; }

    // Record any MachNode for spilling heuristics
    LRG mach( MachNode mach ) { _mach = mach; return this; }

    // Record intersection of all register masks.
    // True if still has registers
    boolean and( RegMask mask ) {
        _mask = mask.and(_mask);
        return !_mask.isEmpty();
    }

    @Override public String toString() { return toString(new SB()).toString(); }

    public SB toString( SB sb ) {
        sb.p("V").p(_lrg);
        if( _mask!=null ) _mask.toString(sb);
        return sb;
    }
}
