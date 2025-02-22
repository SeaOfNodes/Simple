package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;

import java.util.IdentityHashMap;

// Live Range
public class LRG {

    // Dense live range numbers
    final short _lrg;

    // U-F leader; null if leader
    LRG _leader;

    // Choosen register
    short _reg;

    // Count of single-register defs and uses
    short _1regDefCnt, _1regUseCnt;

    // A sample MachNode def in the live range
    MachNode _machDef, _machUse;
    short _uidx;                // _machUse input

    // Some splits used in biased coloring
    MachConcreteNode _splitDef, _splitUse;

    // All the self-conflicting defs for this live range
    IdentityHashMap<Node,String> _selfConflicts;

    // AND of all masks involved; null if none have been applied yet
    RegMask _mask;

    // Adjacent Live Range neighbors.  Only valid during coloring
    Ary<LRG> _adj;

    void addNeighbor(LRG lrg) {
        if( _adj==null ) _adj = new Ary<>(LRG.class);
        _adj.push(lrg);
    }

    // Remove and compress neighbor list; store ex-neighbor past
    // end for unwinding Simplify.  True if exactly going low-degree
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

    LRG( short lrg ) { _lrg = lrg; _reg = -1; }

    boolean leader() { return _leader == null; }

    LRG find() {
        if( _leader==null ) return this; // I am the leader
        if( _leader._leader==null ) // I point to the leader
            return _leader;
        return _rollup();
    }
    private LRG _rollup() {
        LRG ldr = _leader._leader;
        // Roll-up
        while( ldr._leader!=null ) ldr = ldr._leader;
        LRG l2 = this;
        while( l2 != ldr ) {
            LRG l3 = l2._leader;
            l2._leader = ldr;
            l2 = l3;
        }
        return ldr;
    }

    LRG union( LRG lrg ) {
        assert leader();
        if( lrg==null ) return this;
        lrg = lrg.find();
        if( lrg==this ) return this;
        return _lrg < lrg._lrg ? _union(lrg) : lrg._union(this);
    }
    private LRG _union( LRG lrg ) {
        // Set U-F leader
        lrg._leader = this;
        // Fold together stats
        if( _machDef==null ) {
            _machDef = lrg._machDef;
        } else if( lrg._machDef!=null ) {
            if( _1regDefCnt==0 )
                _machDef = lrg._machDef;
            else if( _machDef==lrg._machDef )
                _1regDefCnt--;
        }
        _1regDefCnt += lrg._1regDefCnt;

        if( _machUse==null ) {
            _machUse = lrg._machUse;
        } else if( lrg._machUse!=null ) {
            if( _1regUseCnt==0 )
                _machUse = lrg._machUse;
            else if( _machUse==lrg._machUse )
                _1regUseCnt--;
        }
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
        if( def.isSplit() && (_splitDef==null || ((MachConcreteNode)def).cfg0().loopDepth() > _splitDef.cfg0().loopDepth()) )
            _splitDef = (MachConcreteNode)def;
        return this;
    }

    // Record any Mach use for spilling heuristics
    LRG machUse( MachNode use, short uidx, boolean size1 ) {
        if( _machUse==null || size1 )
            { _machUse = use; _uidx = uidx; }
        if( size1 )
            _1regUseCnt++;
        if( use.isSplit() && (_splitUse==null || ((MachConcreteNode)use).cfg0().loopDepth() > _splitUse.cfg0().loopDepth()) )
            _splitUse = (MachConcreteNode)use;
        return this;
    }

    void selfConflict( Node def ) {
        if( _selfConflicts == null ) _selfConflicts = new IdentityHashMap<>();
        _selfConflicts.put(def,"");
    }


    // Record intersection of all register masks.
    // True if still has registers
    boolean and( RegMask mask ) {
        RegMask mask2 = mask.and(_mask);
        if( mask2==null )
            mask2 = _mask.copy().and(mask);
        _mask = mask2;
        return !_mask.isEmpty();
    }
    // Remove this singular register
    // True if still has registers
    boolean clr( int reg ) {
        if( _mask.clr(reg) ) return true;
        _mask = _mask.copy();   // Need a mutable copy
        return _mask.clr(reg);
    }
    // Subtract mask (AND complement)
    // True if still has registers
    boolean sub( RegMask mask ) {
        RegMask mask2 = _mask.sub(mask);
        if( mask2==null )
            mask2 = _mask.copy().sub(mask);
        _mask = mask2;
        return !_mask.isEmpty();
    }

    @Override public String toString() { return toString(new SB()).toString(); }

    public SB toString( SB sb ) {
        sb.p("V").p(_lrg);
        if( _mask!=null ) _mask.toString(sb);
        return sb;
    }
}
