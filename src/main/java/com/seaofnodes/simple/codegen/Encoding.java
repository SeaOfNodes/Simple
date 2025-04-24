package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.IRPrinter;
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
    public final CodeGen _code;

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

    // Big Constant relocation info.
    public static class Relo {
        public final Node _op;
        public final Type _t;          // Constant type
        public final byte _off;        // Offset from start of opcode
        public final byte _elf;        // ELF relocation type, e.g. 2/PC32
        public int _target;      // Where constant is finally placede
        public int _opStart;     // Opcode start
        Relo( Node op, Type t, byte off, byte elf ) {
            _op=op;  _t=t;  _off=off; _elf=elf;
        }
    }
    public final HashMap<Node,Relo> _bigCons = new HashMap<>();

    Encoding( CodeGen code ) { _code = code; }

    // Shortcut to the defining register
    public short reg(Node n) {
        return _code._regAlloc.regnum(n);
    }

    public int read1(int idx) {
        byte[] buf = _bits.buf();
        return (buf[idx]&0xFF);
    }
    public int read2(int idx) {
        byte[] buf = _bits.buf();
        return (buf[idx]&0xFF) | (buf[idx+1]&0xFF) <<8;
    }
    public int read4(int idx) {
        byte[] buf = _bits.buf();
        return (buf[idx]&0xFF) | (buf[idx+1]&0xFF) <<8 | (buf[idx+2]&0xFF)<<16 | (buf[idx+3]&0xFF)<<24;
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

    static void padN(int n, BAOS bits) {
        while( (bits.size()+n-1 & -n) > bits.size() )
            bits.write(0);
    }

    // Convenience for writing log-N
    static void addN( int log, Type t, BAOS bits ) {
        long x = t instanceof TypeInteger ti
            ? ti.value()
            : log==3
            ? Double.doubleToRawLongBits(    ((TypeFloat)t).value())
            : Float.floatToRawIntBits((float)((TypeFloat)t).value());
        addN(log,x,bits);
    }
    static void addN( int log, long x, BAOS bits ) {
        for( int i=0; i < 1<<log; i++ ) {
            bits.write((int)x);
            x >>= 8;
        }
    }

    public Encoding add1( int op ) { addN(0,op,_bits); return this; }
    public Encoding add2( int op ) { addN(1,op,_bits); return this; }
    public Encoding add4( int op ) { addN(2,op,_bits); return this; }
    public Encoding add8(long op ) { addN(3,op,_bits); return this; }


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
    public void largeConstant( Node relo, Type t, int off, int elf ) {
        assert t.isConstant();
        assert (byte)off == off;
        assert (byte)elf == elf;
        _bigCons.put(relo,new Relo(relo,t,(byte)off,(byte)elf));
    }

    // --------------------------------------------------
    void encode() {
        // Basic block layout: negate branches to keep blocks in-order; insert
        // unconditional jumps.  Attempt to keep backwards branches taken,
        // forwards not-taken (this is the default prediction on most
        // hardware).  Layout is still Reverse Post Order but with more
        // restrictions.
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

    // --------------------------------------------------
    // Basic block layout: negate branches to keep blocks in-order; insert
    // unconditional jumps.  Attempt to keep backwards branches taken, forwards
    // not-taken (this is the default prediction on most hardware).  Layout is
    // still Reverse Post Order but with more restrictions.
    private void basicBlockLayout() {
        IdentityHashMap<LoopNode,Ary<CFGNode>> rpos = new IdentityHashMap<>();
        Ary<CFGNode> rpo = new Ary<>(CFGNode.class);
        rpos.put(_code._start.loop(),rpo);
        BitSet visit = _code.visit();
        //IdentityHashMap<CFGNode,LoopNode> looptail = new IdentityHashMap<>();
        rpo.add(_code._stop);
        for( Node n : _code._start._outputs )
            if( n instanceof FunNode fun ) {
                int x = rpo._len;
                //_rpo_cfg2(fun, visit, rpo, looptail);
                _rpo_cfg(fun, visit, rpos );
                assert rpo.at(x) instanceof ReturnNode;
            }
        rpo.add(_code._start);

        // Reverse in-place
        for( int i=0; i< rpo.size()>>1; i++ )
            rpo.swap(i,rpo.size()-1-i);
        visit.clear();
        _code._cfg = rpo;       // Save the new ordering
    }


    private void _rpo_cfg(CFGNode bb, BitSet visit, IdentityHashMap<LoopNode,Ary<CFGNode>> rpos ) {
        if( bb==null || visit.get(bb._nid) ) return;
        visit.set(bb._nid);
        CFGNode next = bb.uctrl();

        // Loops run an inner "rpo_cfg", then append the entire loop body in
        // place.  This keeps loop bodies completely contained, although if
        // Some Day Later we have real profile data we ought to arrange the
        // branch orderings based on frequency.
        if( bb instanceof LoopNode loop ) {
            Ary<CFGNode> body = new Ary<>(CFGNode.class); // Private RPO for the loop
            rpos.put(loop.loop(),body);                   // Find it via loop tree
            _rpo_cfg(next,visit,rpos);                    // RPO the loop
            body.add(loop);                               // Include loop last (first in RPO)
            Ary<CFGNode> outer = rpos.get(loop.cfg(1).loop());
            outer.addAll(body); // Append to original CFG
            return;
        }

        // IfNodes visit 2 sides, and may choose to reorder them in the RPO
        if( bb instanceof IfNode iff ) {
            // Pick out the T/F projections
            CProjNode t = iff.cproj(0);
            CProjNode f = iff.cproj(1);
            // Invert the branch or not
            if( shouldInvert(t,f,iff.loopDepth()) ) {
                iff.negate();
                t.invert();
                f.invert();
                CProjNode tmp=f; f=t; t=tmp; // Swap t/f
            }

            // Whichever side is visited first becomes last in the RPO.  With
            // no loops, visit the False side last (so True side first) so that
            // when the False RPO visit returns, the IF is immediately next.
            // When the RPO is reversed, the fall-through path will always be
            // following the IF.
            _rpo_cfg(t,visit,rpos); // True side first
            next = f;               // False side last
        }

        // If the *next* BB has already been visited, and we are not already a
        // jump, we may need an unconditional forwards jump here
        Ary<CFGNode> rpo = rpos.get(bb.loop());
        if( next!=null && visit.get(next._nid) && !(bb instanceof IfNode) ) {
            boolean needJump = next instanceof LoopNode
                // Empty blocks from an IF will invert the IF and backwards
                // branch to the loop head.  If not empty, or not an IF
                // will need a jump.
                ? (bb.nOuts()>1 || !(bb.cfg0() instanceof IfNode) )
                // Forwards jump.  If all the blocks, in RPO order, to our
                // target are empty, we will fall in and not need a jump.
                : !backwardsEmptyScan(rpo,next);
            if( needJump ) {
                CFGNode jmp = _code._mach.jump();
                jmp._ltree = bb._ltree;
                jmp.setDefX(0,bb);
                next.setDef(next._inputs.find(bb),jmp);
                rpo.add(jmp);
            }
        }

        _rpo_cfg(next,visit,rpos);
        rpo.add(bb);
    }

    // Should this test be inverted?
    private static boolean shouldInvert(CFGNode t, CFGNode f, int bld) {
        int tld = t.loopDepth(), fld = f.loopDepth();
        // These next two are symmetric and can happen in any order; if `tld <
        // bld` is true, the `fld < bld` must be false, or else both directions
        // exit the loop... and the IF test would not be in the loop.

        // true to exit a loop usually (and false falls into Yet Another Loop
        // Block), invert if false is taking an empty backwards branch.
        if( tld < bld ) return  forwardsEmptyScan(f,bld);
        // false to exit a loop, normally invert to true (except empty back)
        if( fld < bld ) return !forwardsEmptyScan(t,bld);

        // Not exiting the loop; staying at this depth (or going deeper loop).

        // Jump/true to an empty backwards block
        if( forwardsEmptyScan(t,bld) ) return false;
        if( forwardsEmptyScan(f,bld) ) return true ;

        // Fall/false into a full block, Jump/true to an empty block.
        if( f.nOuts()>1 && t.nOuts()==1 ) return false;
        if( t.nOuts()>1 && f.nOuts()==1 ) return true ;
        // Everything else equal, use pre-order
        return t._pre > f._pre;
    }

    // Do we continue forwards from 'c' to the Loop back edge, without doing
    // anything else?  Then this branch can directly jump to the loop head.
    private static boolean forwardsEmptyScan( CFGNode c, int bld ) {
        if( c.nOuts()!=1 || c.loopDepth()!=bld ) return false;
        return c.uctrl() instanceof RegionNode cfg &&
            (cfg instanceof LoopNode || forwardsEmptyScan(cfg,bld));
    }

    // Is the CFG from "next" to the end empty?  This means jumping to "next"
    // will naturally fall into the end.
    private static boolean backwardsEmptyScan(Ary<CFGNode> rpo, CFGNode next) {
        for( int i=rpo._len-1; rpo.at(i)!=next; i-- )
            if( rpo.at(i).nOuts()!=1 )
                return false;
        return true;
    }

    // --------------------------------------------------
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
    }

    // --------------------------------------------------
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
                // Functions pad to align 16
                if( bb instanceof FunNode ) {
                    int newStart = _opStart[bb._nid]+slide;
                    slide += (newStart+15 & -16)-newStart;
                }
                _opStart[bb._nid] += slide;
                // Slide down all other (non-CFG) ops in the block
                for( Node n : bb._outputs )
                    if( n instanceof MachNode && !(n instanceof CFGNode) )
                        _opStart[n._nid] += slide;
                if( bb instanceof RIPRelSize riprel ) {
                    CFGNode target = (bb instanceof IfNode iff ? iff.cproj(0) : (CFGNode)bb.out(0)).uctrlSkipEmpty();
                    // Delta is from opStart to opStart.  X86 at least counts
                    // the delta from the opEnd, but we don't have the end until
                    // we decide the size - so the encSize has to deal
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


    // --------------------------------------------------
    // Patch local encodings now
    void patchLocalRelocations() {
        // Walk the local code-address relocations
        for( Node src : _internals.keySet() ) {
            int start  = _opStart[src._nid];
            Node dst =  _internals.get(src);
            int target = _opStart[dst._nid];
            ((RIPRelSize)src).patch(this, start, _opLen[src._nid], target - start);
        }
    }

    // --------------------------------------------------
    // Write the constant pool into the BAOS and optionally patch locally
    void writeConstantPool( BAOS bits, boolean patch ) {
        padN(16,bits);

        HashMap<Type,Integer> targets = new HashMap<>();

        // By log size
        for( int log = 3; log >= 0; log-- ) {
            // Write the 8-byte constants
            for( Node op : _bigCons.keySet() ) {
                Relo relo = _bigCons.get(op);
                if( relo._t.log_size()==log ) {
                    // Map from relo to constant start and patch
                    Integer target = targets.get(relo._t);
                    if( target==null ) {
                        targets.put(relo._t,target = bits.size());
                        // Put constant into code space.
                        if( relo._t instanceof TypeTuple tt ) // Constant tuples put all entries
                            for( Type tx : tt._types )
                                addN(log,tx,bits);
                        else
                            addN(log,relo._t,bits);
                    }
                    relo._target = target;
                    relo._opStart= _opStart[op._nid];
                    // Go ahead and locally patch in-memory
                    if( patch )
                        ((RIPRelSize)op).patch(this, relo._opStart, _opLen[op._nid], relo._target - relo._opStart);
                }
            }
        }
    }


    // A series of libc/external calls that Simple can link against in a JIT.
    // Since no runtime in the JVM process, using magic numbers for the CPU
    // emulators to pick up on.
    public static int SENTINAL_CALLOC = -4;

    void patchGlobalRelocations() {
        for( Node src : _externals.keySet() ) {
            int start  = _opStart[src._nid];
            String dst =  _externals.get(src);
            int target = switch( dst ) {
            case "calloc" -> SENTINAL_CALLOC;
            default -> throw Utils.TODO();
            };
            ((RIPRelSize)src).patch(this, start, _opLen[src._nid], target - start);
        }
    }
}
