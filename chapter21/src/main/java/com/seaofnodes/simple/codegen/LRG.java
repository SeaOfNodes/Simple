package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;

import java.util.IdentityHashMap;

/** A Live Range
 * <p>
 *  A live range is a set of nodes and edges which must get the same register.
 *  Live ranges form an interconnected web with almost no limits on their
 *  shape.  Live ranges also gather the set of register constraints from all
 *  their parts.
 * <p>
 *  Most of the fields in this class are for spill heuristics, but there are a
 *  few key ones that define what a LRG is.  LRGs have a unique dense integer
 *  `_lrg` number which names the LRG.  New `_lrg` numbers come from the
 *  `RegAlloc._lrg_num` counter.  LRGs can be unioned together -
 *  (<a href="https://en.wikipedia.org/wiki/Disjoint-set_data_structure">this is the Union-Find algorithm</a>)
 *  - and when this happens the lower numbered `_lrg` wins.  Unioning only
 *  happens during `BuildLRG` and happens because either a `Phi` is forcing all
 *  its inputs and outputs into the same register, or because of a 2-address
 *  instruction.  LRGs have matching `union` and `find` calls, and a set
 *  `_leader` field.
 */
public class LRG {

    // Dense live range numbers
    final short _lrg;

    // U-F leader; null if leader
    LRG _leader;

    // Choosen register
    short _reg;

    // Count of single-register defs and uses
    short _1regDefCnt, _1regUseCnt;
    // Count of all defs, uses.  Mostly interested 1 vs many
    boolean _multiDef, _multiUse;

    // A sample MachNode def in the live range
    public MachNode _machDef, _machUse;
    short _uidx;                // _machUse input

    // Some splits used in biased coloring
    MachConcreteNode _splitDef, _splitUse;

    // All the self-conflicting defs for this live range
    IdentityHashMap<Node,String> _selfConflicts;

    // AND of all masks involved; null if none have been applied yet
    RegMask _mask;

    // Adjacent Live Range neighbors.  Only valid during coloring
    Ary<LRG> _adj;

    public int nadj() { return _adj==null ? 0 : _adj._len; }

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
    boolean lowDegree() { return nadj() < _mask.size(); }

    LRG( short lrg ) { _lrg = lrg; _reg = -1; }

    boolean leader() { return _leader == null; }

    // Fast-path Find from the Union-Find algorithm
    LRG find() {
        if( _leader==null ) return this; // I am the leader
        if( _leader._leader==null ) // I point to the leader
            return _leader;
        return _rollup();
    }
    // Slow-path rollup of U-F
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

    // Union `this` and `lrg`, keeping the lower numbered _lrg.
    // Includes a number of fast-path cutouts.
    LRG union( LRG lrg ) {
        assert leader();
        if( lrg==null ) return this;
        lrg = lrg.find();
        if( lrg==this ) return this;
        return _lrg < lrg._lrg ? _union(lrg) : lrg._union(this);
    }
    // Union `this` and `lrg`, folding together all stats.
    private LRG _union( LRG lrg ) {
        // Set U-F leader
        lrg._leader = this;
        // Fold together stats
        if( _machDef==null ) {
            _machDef = lrg._machDef;
        } else if( lrg._machDef!=null ) {
            if( _machDef != lrg._machDef ) _multiDef=true;
            if( _1regDefCnt==0 )
                _machDef = lrg._machDef;
            else if( _machDef==lrg._machDef )
                _1regDefCnt--;
        }
        _1regDefCnt += lrg._1regDefCnt;
        _multiDef |= lrg._multiDef;

        if( _machUse==null ) {
            _machUse = lrg._machUse;
            _uidx = lrg._uidx;
        } else if( lrg._machUse!=null ) {
            if( _machUse != lrg._machUse ) _multiUse=true;
            if( _1regUseCnt==0 ) {
                _machUse = lrg._machUse;
                _uidx = lrg._uidx;
            }
            else if( _machUse==lrg._machUse )
                _1regUseCnt--;
        }
        _1regUseCnt += lrg._1regUseCnt;
        _multiUse |= lrg._multiUse;

        // Fold deepest Split
        _splitDef = deepSplit(_splitDef,lrg._splitDef);
        _splitUse = deepSplit(_splitUse,lrg._splitUse);

        // Fold together masks
        RegMask mask = _mask.and(lrg._mask);
        if( mask==null )
            mask = _mask.copy().and(lrg._mask);
        _mask = mask;
        return this;
    }

    private static MachConcreteNode deepSplit( MachConcreteNode s0, MachConcreteNode s1 ) {
        return s0==null || (s1!=null && s0.cfg0().loopDepth() < s1.cfg0().loopDepth()) ? s1 : s0;
    }

    // Record any Mach def for spilling heuristics
    LRG machDef( MachNode def, boolean size1 ) {
        if( _machDef!=null && _machDef!=def )
            _multiDef = true;
        if( _machDef==null || size1 )
            _machDef = def;
        if( size1 )
            _1regDefCnt++;
        if( def instanceof SplitNode split )
            _splitDef = deepSplit(_splitDef,split);
        return this;
    }

    // Record any Mach use for spilling heuristics
    LRG machUse( MachNode use, short uidx, boolean size1 ) {
        if( _machUse!=null && _machUse!=use )
            _multiUse = true;
        if( _machUse==null || size1 )
            { _machUse = use; _uidx = uidx; }
        if( size1 )
            _1regUseCnt++;
        if( use instanceof SplitNode split )
            _splitUse = deepSplit(_splitUse,split);
        return this;
    }

    boolean hasSplit() { return _splitDef != null || _splitUse != null; }
    short size() { return _mask.size(); }
    boolean size1() { return _mask.size1(); }


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
