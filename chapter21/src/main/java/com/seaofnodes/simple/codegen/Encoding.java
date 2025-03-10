package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;


/**
 *  Instruction encodings
 *
 *  This class holds the encoding bits, plus the relocation information needed
 *  to move the code to a non-zero offset, or write to an external file (ELF).
 *
 *  There are also a bunch of generic utilities for managing bits and bytes
 *  common in all encodings.
 */
public class Encoding {

    // Top-level program graph structure
    final CodeGen _code;

    // Instruction bytes.  The registers are encoded already.  Relocatable data
    // is in a fixed format depending on the kind of relocation.

    // - RIP-relative offsets into the same chunk are just encoded with their
    //   check relative offsets; no relocation info is required.

    // - Fixed address targets in the same encoding will be correct for a zero
    //   base; moving the entire encoding requires adding the new base address.

    // - RIP-relative to external chunks have a zero offset; the matching
    //   relocation info will be used to patch the correct value.

    final ByteArrayOutputStream _bits;


    Encoding( CodeGen code ) {
        _code = code;
        _bits = new ByteArrayOutputStream();
    }


    void encode() {
        // Basic block layout.  Now that RegAlloc is finished, no more spill
        // code will appear.  We can change our BB layout from RPO to something
        // that minimizes actual branches, takes advantage of fall-through
        // edges, and tries to help simple branch predictions: back branches
        // are predicted taken, forward not-taken.
        Ary<CFGNode> rpo = new Ary<>(CFGNode.class);
        BitSet visit = _code.visit();
        for( Node n : _code._start._outputs )
            if( n instanceof FunNode fun )
                _rpo_cfg(fun, visit, rpo);
        rpo.add(_code._start);
        // Reverse in-place
        for( int i=0; i< rpo.size()>>1; i++ )
            rpo.swap(i,rpo.size()-1-i);
        visit.clear();
        _code._cfg = rpo;       // Save the new ordering

        // Write encoding bits in order
        //for( CFGNode bb : _code._cfg )
        //    for( Node n : bb._outputs )
        //        if( n instanceof MachNode mach )
        //            throw Utils.TODO();
    }

    // Post-Order of CFG
    private static void _rpo_cfg(CFGNode bb, BitSet visit, Ary<CFGNode> rpo) {
        if( visit.get(bb._nid) ) return;
        visit.set(bb._nid);
        if( bb.nOuts()==0 ) return; // StopNode
        if( !(bb instanceof IfNode iff) ) {
            CFGNode next = bb instanceof ReturnNode ? (CFGNode)bb.out(bb.nOuts()-1) : bb.uctrl();
            _rpo_cfg(next,visit,rpo);
        } else {
            // Pick out the T/F projections
            CProjNode t = (CProjNode)bb.out(bb.nOuts()-1);
            CProjNode f = (CProjNode)bb.out(bb.nOuts()-2);
            if( t._idx==1 ) { CProjNode tmp=f; f=t; t=tmp; }
            int tld = t.loopDepth(), fld = f.loopDepth(), bld = bb.loopDepth();
            // Decide entering or exiting a loop
            if( tld==bld ) {
                // if T is empty, keep
                if( t.nOuts()>1 &&
                    // Else swap so T is forward and exits, while F falls into next loop block
                    ((fld<bld && t.out(0)!=t.loop()) ||
                     // Jump to an empty block and fall into a busy block
                     (fld==bld && f.nOuts()==1)) ) {
                    // Invert test and Proj fields
                    iff.invert();
                    t.invert();
                    f.invert();
                    CProjNode tmp=f; f=t; t=tmp; // Swap t/f
                } // Else nothing
            } else if( tld > bld ) { // True enters a deeper loop
                throw Utils.TODO();
            } // Else True exits a loop, always forward and good

            // Always visit the False side last (so True side first), so that
            // when the False RPO visit returns, the IF is immediately next.
            // When the RPO is reversed, the fall-through path will always be
            // following the IF.
            _rpo_cfg(t,visit,rpo);
            _rpo_cfg(f,visit,rpo);
        }
        rpo.add(bb);
    }


}
