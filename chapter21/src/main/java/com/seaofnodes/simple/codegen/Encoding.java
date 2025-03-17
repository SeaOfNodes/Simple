package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;


/**
 *  Instruction encodings
 * <p>
 *  This class holds the encoding bits, plus the relocation information needed
 *  to move the code to a non-zero offset, or write to an external file (ELF).
 * <p>
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

    public static class BAOS extends ByteArrayOutputStream {
        public byte[] buf() { return buf; }
        void set( byte[] buf0, int count0 ) { buf=buf0; count=count0; }
    };
    final public BAOS _bits;

    public int [] _opStart;     // Start  of opcodes, by _nid
    public byte[] _opLen;       // Length of opcodes, by _nid

    Encoding( CodeGen code ) {
        _code = code;
        _bits = new BAOS();
        _bigCons = new HashMap<>();
        _jmps = new HashMap<>();
    }

    // Shortcut to the defining register
    public short reg(Node n) {
        return _code._regAlloc.regnum(n);
    }

    public Encoding add1( int op ) { _bits.write(op); return this; }

    // Little endian write of a 32b opcode
    public void add4( int op ) {
        _bits.write(op    );
        _bits.write(op>> 8);
        _bits.write(op>>16);
        _bits.write(op>>24);
    }
    public void add8( long i64 ) {
        add4((int) i64     );
        add4((int)(i64>>32));
    }

    // This buffer is invalid/moving until after all encodings are written
    public byte[] bits() { return _bits.buf(); }

    // 4 byte little-endian write
    public void patch4( int idx, int val ) {
        byte[] buf = _bits.buf();
        buf[idx  ] = (byte)(val    );
        buf[idx+1] = (byte)(val>> 8);
        buf[idx+2] = (byte)(val>>16);
        buf[idx+3] = (byte)(val>>24);
    }


    // Relocation thinking:
    // `encoding()` calls back with info (TFP needed, branch target needed).
    // Record start of op & TFP/target info.

    // During some future RELO phase, after TFP layout/targets known
    // call back with `ReloNode.patch(byte[],src_offset,TFP,dst_offset)`
    // X86 gets a special pass for expanding short jumps.

    public void relo( Node relo, TypeFunPtr t ) {
        // TODO: record call relocation info
    }
    public void relo( NewNode nnn ) {
        // TODO: record alloc relocation info
    }
    // Store t as a 32/64 bit constant in the code space; generate RIP-relative
    // addressing to load it

    private final HashMap<Node,Type> _bigCons;
    public void largeConstant( Node relo, Type t ) {
        assert t.isConstant();
        _bigCons.put(relo,t);
        // TODO:
    }
    private final HashMap<CFGNode,CFGNode> _jmps;
    public void jump( CFGNode jmp, CFGNode target ) { _jmps.put(jmp,target); }

    void encode() {
        // Basic block layout: invert branches to keep blocks in-order; insert
        // unconditional jumps.  Attempt to keep backwards branches taken,
        // forwards not-taken (this is the default prediction on most
        // hardware).  Layout is still RPO but with more restrictions.
        basicBlockLayout();

        // Write encoding bits in order into a big byte array.
        // Record opcode start and length.
        writeEncodings();

        // Short-form RIP-relative support: replace long encodings with short
        // encodings and compact the code, changing all the offsets.
        compactShortForm();

        // Patch RIP-relative and local encodings now.
        patchLocalRelocations();
    }

    // Basic block layout: invert branches to keep blocks in-order; insert
    // unconditional jumps.  Attempt to keep backwards branches taken,
    // forwards not-taken (this is the default prediction on most
    // hardware).  Layout is still RPO but with more restrictions.
    private void basicBlockLayout() {
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
    }

    // Basic block layout.  Now that RegAlloc is finished, no more spill code
    // will appear.  We can change our BB layout from RPO to something that
    // minimizes actual branches, takes advantage of fall-through edges, and
    // tries to help simple branch predictions: back branches are predicted
    // taken, forward not-taken.
    private void _rpo_cfg(CFGNode bb, BitSet visit, Ary<CFGNode> rpo) {
        if( visit.get(bb._nid) ) return;
        visit.set(bb._nid);
        if( bb.nOuts()==0 ) return; // StopNode
        if( !(bb instanceof IfNode iff) ) {
            CFGNode next = bb instanceof ReturnNode ? (CFGNode)bb.out(bb.nOuts()-1) : bb.uctrl();
            // If the *next* BB has already been visited, we require an
            // unconditional backwards jump here
            if( visit.get(next._nid) && !(next instanceof StopNode) && !(bb.in(0) instanceof IfNode )) {
                CFGNode jmp = _code._mach.jump();
                jmp.setDefX(0,bb);
                next.setDef(next._inputs.find(bb),jmp);
                rpo.add(jmp);
            }
            _rpo_cfg(next,visit,rpo);
        } else {
            boolean invert = false;
            // Pick out the T/F projections
            CProjNode t = iff.cproj(0);
            CProjNode f = iff.cproj(1);
            //CProjNode t = (CProjNode)bb.out(bb.nOuts()-1);
            //CProjNode f = (CProjNode)bb.out(bb.nOuts()-2);
            //if( t._idx==1 ) { CProjNode tmp=f; f=t; t=tmp; }
            int tld = t.loopDepth(), fld = f.loopDepth(), bld = bb.loopDepth();
            // Decide entering or exiting a loop
            if( tld==bld ) {
                // if T is empty, keep
                if( t.nOuts()>1 &&
                    // Else swap so T is forward and exits, while F falls into next loop block
                    ((fld<bld && t.out(0)!=t.loop()) ||
                     // Jump to an empty block and fall into a busy block
                     (fld==bld && f.nOuts()==1)) ) {
                    invert = true;
                } // Else nothing
            } else if( tld > bld ) { // True enters a deeper loop
                throw Utils.TODO();
            } else if( fld == bld && f.out(0) instanceof LoopNode ) { // Else True exits a loop
                // if false is the loop backedge, make sure its true/taken
                invert = true;
            } // always forward and good
            // Invert test and Proj fields
            if( invert ) {
                iff.invert();
                t.invert();
                f.invert();
                CProjNode tmp=f; f=t; t=tmp; // Swap t/f
            }

            // Always visit the False side last (so True side first), so that
            // when the False RPO visit returns, the IF is immediately next.
            // When the RPO is reversed, the fall-through path will always be
            // following the IF.
            _rpo_cfg(t,visit,rpo);
            _rpo_cfg(f,visit,rpo);
        }
        rpo.add(bb);
    }


    // Write encoding bits in order into a big byte array.
    // Record opcode start and length.
    private void writeEncodings() {
        _opStart= new int [_code.UID()];
        _opLen  = new byte[_code.UID()];
        for( CFGNode bb : _code._cfg ) {
            if( !(bb instanceof MachNode) ) _opStart[bb._nid] = _bits.size();
            for( Node n : bb._outputs ) {
                if( n instanceof MachNode mach ) {
                    _opStart[n._nid] = _bits.size();
                    mach.encoding( this );
                    _opLen[n._nid] = (byte) (_bits.size() - _opStart[n._nid]);
                }
            }
        }
    }


    // Short-form RIP-relative support: replace long encodings with short
    // encodings and compact the code, changing all the offsets.
    private void compactShortForm() {
        int len = _code._cfg._len;
        int[] oldStarts = new int[len];
        for( int i=0; i<len; i++ )
            oldStarts[i] = _opStart[_code._cfg.at(i)._nid];

        // TODO: Rewrite this algo to use the small "_jmps" list of just the
        // jumps instead of walking all blocks.
        // TODO2: Allow short encodings on non-jumps.  This algo doesn't
        // really care jumps vs other rip-relative things.

        // Check the size of short jumps, adjust the block starts for a whole
        // pass over all blocks.  If some jumps go long, they might stretch the
        // distance for other jumps, so another pass is needed;
        int slide= -1;
        while( slide != 0) {    // While no fails
            slide = 0;
            for( int i=0; i<len; i++ ) {
                CFGNode bb = _code._cfg.at(i);
                _opStart[bb._nid] += slide;
                if( bb instanceof RIPRelSize riprel ) {
                    CFGNode target = (CFGNode)bb.out(0);
                    // Delta is from opStart to opStart.  X86 at least counts
                    // the delta from the opEnd, but we don't have the end until
                    // we decide the size - so the encSize has to deal
                    assert _opStart[target._nid] > 0;
                    int delta = _opStart[target._nid] - _opStart[bb._nid];
                    byte opLen = riprel.encSize(delta);
                    if( _opLen[bb._nid] < opLen ) {
                        slide += opLen - _opLen[bb._nid];
                        _opLen[bb._nid] = opLen;
                    }
                }
            }
        }


        // Copy/slide the bits to make space for all the longer branches
        int grow = _opStart[_code._cfg.at(len-1)._nid] - oldStarts[len-1];
        if( grow > 0 ) {        // If no short-form ops, nothing to do here
            int end = _bits.size();
            byte[] bits = new byte[end+grow];
            for( int i=len-1; i>=0; i-- ) {
                int start = oldStarts[i];
                if( start==0 && i>1 ) continue;
                int oldStart = oldStarts[i];
                int newStart = _opStart[_code._cfg.at(i)._nid];
                System.arraycopy(_bits.buf(),oldStart,bits,newStart,end-start);
                end = start;
            }
            _bits.set(bits,bits.length);
        }
    }

    // Patch local encodings now
    private void patchLocalRelocations() {
        // Walk all the jumps.  Re-patch them all now with but with the Real Offset
        for( CFGNode jmp : _jmps.keySet() ) {
            CFGNode target = jmp instanceof IfNode iff ? iff.cproj(0) : jmp.uctrl();
            while( target.nOuts() == 1 ) // Skip empty blocks
                target = target.uctrl();
            int start = _opStart[jmp._nid];
            ((RIPRelSize)jmp).patch(this, start, _opLen[jmp._nid], _opStart[target._nid] - start);
        }
    }

}
