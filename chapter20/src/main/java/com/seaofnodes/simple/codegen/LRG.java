package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.MachNode;

// Live Range
public class LRG {

    // Dense live range numbers
    final short _lrg;

    // U-F leader; null if leader
    LRG _leader;

    // Count of single-register defs and uses
    short _1regDefCnt, _1regUseCnt;

    // A sample MachNode def in the live range
    MachNode _machDef, _machUse;
    short _uidx;                // _machUse input

    // AND of all masks involved; null if none have been applied yet
    RegMask _mask;

    // Adjacent Live Range neighbors.  Only valid during coloring
    Ary<LRG> _adj;
    void addNeighbor(LRG lrg) {
        if( _adj==null ) _adj = new Ary<>(LRG.class);
        _adj.push(lrg);
    }

    // Remove and compress neighbor list; store ex-neighbor past
    // end for unwinding Simplify.  If true if exactly going low-degree
    boolean removeCompress( LRG lrg ) {
        _adj.swap(_adj.find(lrg),_adj._len-1);
        return _adj._len-- == _mask.size();
    }

    void reinsert( LRG lrg ) {
        assert _adj._es[_adj._len]==lrg;
        _adj._len++;
    }

    // More registers than neighbors
    boolean lowDegree() { return (_adj==null ? 0 : _adj._len) < _mask.size(); }

    // Choosen register
    short _reg;

    LRG( short lrg ) { _lrg = lrg; _reg = -1; }

    boolean unified() { return _leader!=null; }

    LRG find() {
        if( _leader==null )
            return this;
        if( _leader._leader==null )
            return _leader;
        throw Utils.TODO();
    }

    LRG union( LRG lrg ) {
        if( lrg==null || lrg==this ) return this;
        return _lrg < lrg._lrg ? _union(lrg) : lrg._union(this);
    }
    private LRG _union( LRG lrg ) {
        // Set U-F leader
        lrg._leader = this;
        // Fold together stats
        if( _machDef == lrg._machDef && _1regDefCnt > 0 ) _1regDefCnt--;
        if( _machDef == null ) _machDef = lrg._machDef;
        _1regDefCnt += lrg._1regDefCnt;

        if( _machUse == lrg._machUse && _uidx == lrg._uidx && _1regUseCnt > 0 ) _1regUseCnt--;
        if( _machUse == null ) { _machUse = lrg._machUse; _uidx = lrg._uidx; }
        _1regUseCnt += lrg._1regUseCnt;

        // Fold together masks
        _mask = _mask.and(lrg._mask);
        return this;
    }

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
