package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.MachNode;

// Live Range
public class LRG {

    // Dense live range numbers
    final short _lrg;

    // Count of single-register defs and uses
    short _1regDefCnt, _1regUseCnt;

    // A sample MachNode def in the live range
    MachNode _machDef, _machUse;
    short _uidx;                // _machUse input

    // AND of all masks involved; null if none have been applied yet
    RegMask _mask;

    LRG( short lrg ) { _lrg = lrg; }

    // Record any Mach def for spilling heuristics
    LRG machDef( MachNode def, boolean size1 ) {
        if( _machDef==null || size1 )
            _machDef = def;
        if( size1 )
            _1regDefCnt++;
        return this;
    }

    // Record any Mach use for spilling heuristics
    LRG machUse( MachNode use, short uidx, boolean size1 ) {
        if( _machUse==null || size1 )
            { _machUse = use; _uidx = uidx; }
        if( size1 )
            _1regUseCnt++;
        return this;
    }

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
