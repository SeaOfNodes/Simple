package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.util.*;


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
    final public BAOS _bits = new BAOS();

    public int [] _opStart;     // Start  of opcodes, by _nid
    public byte[] _opLen;       // Length of opcodes, by _nid

    Encoding( CodeGen code ) { _code = code; }

    // Shortcut to the defining register
    public short reg(Node n) {
        return _code._regAlloc.regnum(n);
    }

    public Encoding add1( int op ) { _bits.write(op); return this; }

    public void add2( int op ) {
        _bits.write(op    );
        _bits.write(op>> 8);
    }
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
    public int read4(int idx) {
        byte[] buf = _bits.buf();
        return buf[idx] | (buf[idx+1]&0xFF) <<8 | (buf[idx+2]&0xFF)<<16 | (buf[idx+3]&0xFF)<<24;
    }
    public long read8(int idx) {
        return (read4(idx) & 0xFFFFFFFFL) | read4(idx+4);
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

    void pad8() {
        while( (_bits.size()+7 & -8) > _bits.size() )
            _bits.write(0);
    }


    // Nodes need "relocation" patching; things done after code is placed.
    // Record src and dst Nodes.
    private final HashMap<Node,CFGNode> _internals = new HashMap<>();
    // Source is a Call, destination in the Fun.
    public Encoding relo( CallNode call ) {
        _internals.put(call,_code.link(call.tfp()));
        return this;
    }
    public Encoding relo( ConstantNode con ) {
        TypeFunPtr tfp = (TypeFunPtr)con._con;
        _internals.put(con,_code.link(tfp));
        return this;
    }
    public void jump( CFGNode jmp, CFGNode dst ) {
        _internals.put(jmp,dst.uctrlSkipEmpty());
    }


    final HashMap<Node,String> _externals = new HashMap<>();
    public Encoding external( Node call, String extern ) {
        _externals.put(call,extern);
        return this;
    }

    // Store t as a 32/64 bit constant in the code space; generate RIP-relative
    // addressing to load it

    public final HashMap<Node,Type> _bigCons = new HashMap<>();
    public final HashMap<Node,Integer> _cpool = new HashMap<>();
    public void largeConstant( Node relo, Type t ) {
        assert t.isConstant();
        _bigCons.put(relo,t);
    }

    void encode() {
        // Basic block layout: invert branches to keep blocks in-order; insert
        // unconditional jumps.  Attempt to keep backwards branches taken,
        // forwards not-taken (this is the default prediction on most
        // hardware).  Layout is still RPO but with more restrictions.
        basicBlockLayout();

        // Write encoding bits in order into a big byte array.
        // Record opcode start and length.
        writeEncodings();

        // Write any large constants into a constant pool; they
        // are accessed by RIP-relative addressing.
        writeConstantPool();

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
            if( n instanceof FunNode fun ) {
                int x = rpo._len;
                _rpo_cfg(fun, visit, rpo);
                assert rpo.at(x) instanceof ReturnNode;
            }
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
            // If the *next* BB has already been visited, we may need an
            // unconditional jump here
            if( visit.get(next._nid) && !(next instanceof StopNode) ) {
                boolean needJump = next instanceof LoopNode
                    // If backwards to a loop, and the block has statements,
                    // will need a jump.  Empty blocks can just backwards branch.
                    ? (bb.nOuts()>1)
                    // Forwards jump.  If all the blocks, in RPO order, to our
                    // target are empty, we will fall in and not need a jump.
                    : !isEmptyBackwardsScan(rpo,next);
                if( needJump ) {
                    CFGNode jmp = _code._mach.jump();
                    jmp.setDefX(0,bb);
                    next.setDef(next._inputs.find(bb),jmp);
                    rpo.add(jmp);
                }
            }
            _rpo_cfg(next,visit,rpo);
        } else {
            boolean invert = false;
            // Pick out the T/F projections
            CProjNode t = iff.cproj(0);
            CProjNode f = iff.cproj(1);
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
                int d=tld; tld=fld; fld=d;   // Swap depth
            }

            // Whichever side is visited first becomes last in the RPO.  With
            // no loops, visit the False side last (so True side first) so that
            // when the False RPO visit returns, the IF is immediately next.
            // When the RPO is reversed, the fall-through path will always be
            // following the IF.

            // If loops are involved, attempt to keep them in a line.  Visit
            // the exits first, so they follow the loop body when the order gets
            // reversed.
            if( fld < tld || (fld==tld && f.nOuts()==1) ) {
                _rpo_cfg(f,visit,rpo);
                _rpo_cfg(t,visit,rpo);
            } else {
                _rpo_cfg(t,visit,rpo);
                _rpo_cfg(f,visit,rpo);
            }
        }
        rpo.add(bb);
    }

    private static boolean isEmptyBackwardsScan(Ary<CFGNode> rpo, CFGNode next) {
        for( int i=rpo._len-1; rpo.at(i)!=next; i-- )
            if( rpo.at(i).nOuts()!=1 )
                return false;
        return true;
    }

    // Write encoding bits in order into a big byte array.
    // Record opcode start and length.
    public FunNode _fun;        // Currently encoding function
    private void writeEncodings() {
        _opStart= new int [_code.UID()];
        _opLen  = new byte[_code.UID()];
        for( CFGNode bb : _code._cfg ) {
            if( !(bb instanceof MachNode mach0) )
                _opStart[bb._nid] = _bits.size();
            else if( bb instanceof FunNode fun ) {
                _fun = fun;     // Currently encoding function
                _opStart[bb._nid] = _bits.size();
                mach0.encoding( this );
                _opLen[bb._nid] = (byte) (_bits.size() - _opStart[bb._nid]);
            }
            for( Node n : bb._outputs ) {
                if( n instanceof MachNode mach && !(n instanceof FunNode) ) {
                    _opStart[n._nid] = _bits.size();
                    mach.encoding( this );
                    _opLen[n._nid] = (byte) (_bits.size() - _opStart[n._nid]);
                }
            }
        }
        pad8();
    }

    // Write the constant pool
    private void writeConstantPool() {
        // TODO: Check for cpool dups
        HashSet<Type> ts = new HashSet<>();
        for( Type t : _bigCons.values() ) {
            if( ts.contains(t) )
                throw Utils.TODO(); // Dup!  Compress!
            ts.add(t);
        }

        // Write the 8-byte constants
        for( Node relo : _bigCons.keySet() ) {
            Type t = _bigCons.get(relo);
            if( t.log_size()==3 ) {
                // Map from relo to constant start
                _cpool.put(relo,_bits.size());
                long x = t instanceof TypeInteger ti
                    ? ti.value()
                    : Double.doubleToRawLongBits(((TypeFloat)t).value());
                add8(x);
            }
        }

        // Write the 4-byte constants
        for( Node relo : _bigCons.keySet() ) {
            Type t = _bigCons.get(relo);
            if( t.log_size()==2 ) {
                // Map from relo to constant start
                _cpool.put(relo,_bits.size());
                int x = t instanceof TypeInteger ti
                    ? (int)ti.value()
                    : Float.floatToRawIntBits((float)((TypeFloat)t).value());
                add4(x);
            }
        }
    }

    // Short-form RIP-relative support: replace short encodings with long
    // encodings and expand the code, changing all the offsets.
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
        // distance for other jumps, so another pass is needed.
        int slide = -1;
        while( slide != 0) {    // While no fails
            slide = 0;
            for( int i=0; i<len; i++ ) {
                CFGNode bb = _code._cfg.at(i);
                _opStart[bb._nid] += slide;
                if( bb instanceof RIPRelSize riprel ) {
                    CFGNode target = ((CFGNode)bb.out(0)).uctrlSkipEmpty();
                    // Delta is from opStart to opStart.  X86 at least counts
                    // the delta from the opEnd, but we don't have the end until
                    // we decide the size - so the encSize has to deal
                    assert _opStart[target._nid] > 0;
                    int delta = _opStart[target._nid] - _opStart[bb._nid];
                    byte opLen = riprel.encSize(delta);
                    // Recorded size is smaller than the current size?
                    if( _opLen[bb._nid] < opLen ) {
                        // Start sliding the code down; record slide amount and new size
                        slide += opLen - _opLen[bb._nid];
                        _opLen[bb._nid] = opLen;
                    }
                }
            }

            // CPool padding is non-linear; in rare cases padding can force a
            // larger size...  which will shrink the padding and allow the
            // short form to work.  Too bad.
            if( !_cpool.isEmpty() )
                pad8();
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
        // Walk the local code-address relocations
        for( Node src : _internals.keySet() ) {
            Node dst = _internals.get(src);
            int target = _opStart[dst._nid];
            int start  = _opStart[src._nid];
            ((RIPRelSize)src).patch(this, start, _opLen[src._nid], target - start);
        }

        for( Node src : _cpool.keySet() ) {
            int target = _cpool.get(src);
            int start = _opStart[src._nid];
            ((RIPRelSize)src).patch(this, start, _opLen[src._nid], target - start);
        }
    }


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
    private void frameSize() {
        for( Node n : _code._start.outs() ) {
            if( n instanceof FunNode fun ) {
                throw Utils.TODO();
            }
        }
    }
}
