package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.IRPrinter;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
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

    // CFG Nodes in RPO order, per-encoding.
    public Ary<CFGNode> _cfg;

    // Instruction bytes.  The registers are encoded already.  Relocatable data
    // is in a fixed format depending on the kind of relocation.

    // - RIP-relative offsets into the same chunk are just encoded with their
    //   chunk relative offsets; no relocation info is required.

    // - Fixed address targets in the same encoding will be correct for a zero
    //   base; moving the entire encoding requires adding the new base address.

    // - RIP-relative to external chunks have a zero offset; the matching
    //   relocation info will be used to patch the correct value.

    public final BAOS _bits  = new BAOS(); // Instructions / encodings
    public final BAOS _cpool = new BAOS(); // Constant r/o pool
    public final BAOS _sdata = new BAOS(); // Static   r/w pool
    public final HashMap<Node,Relo> _bigCons = new HashMap<>();

    // Big Constant relocation info.
    public static class Relo {
        public final Node _op;
        public final Type _t;          // Constant type
        public final byte _off;        // Offset from start of opcode
        public final byte _elf;        // ELF relocation type, e.g. 2/PC32
        public int _target;            // Where constant is finally placed
        public int _opStart;           // Opcode start
        Relo( Node op, Type t, byte off, byte elf ) {
            _op=op;  _t=t;  _off=off; _elf=elf;
        }
    }

    Encoding( CodeGen code ) {
        _code = code;
    }

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

    private final static int SENTINEL = -1;
    private final IntHashMap _opStart = new IntHashMap(SENTINEL);
    private final IntHashMap _opLen   = new IntHashMap(SENTINEL);


    // Read op start for n
    public int opStart(Node n) { return _opStart.get(n._nid); }
    // Read op length for n
    public byte opLen (Node n) { return (byte)_opLen.get(n._nid); }

    // Set op start for n
    public void opStart(Node n, int off) {
        assert off != SENTINEL;
        _opStart.put(n._nid,off);
    }

    // Set op start for n
    public void opStartAdd(Node n, int off) {
        if( off==0 ) return;
        int old = _opStart.get(n._nid);
        assert old != SENTINEL && old+off != SENTINEL;
        _opStart.put(n._nid,old+off);
    }

    // Set op length for n
    public void opLen(Node n, byte len) {
        assert len != SENTINEL;
        _opLen.put(n._nid,len);
    }

    // Nodes need "relocation" patching; things done after code is placed.
    // Record src and dst Nodes.
    private final HashMap<Node,CFGNode> _internals = new HashMap<>();
    // Source is a Call, destination in the Fun.
    public Encoding relo( CallNode call ) {
        _internals.put(call,_code.link(call.tfp()));
        return this;
    }
    // Code address as a constant
    public Encoding relo( FunPtrNode fptr ) {
        TypeFunPtr tfp = (TypeFunPtr)fptr._type;
        _internals.put(fptr,_code.link(tfp));
        return this;
    }
    // Local jump
    public void jump( CFGNode jmp, CFGNode dst ) {
        _internals.put(jmp,dst.uctrlSkipEmpty());
    }

    // External references only located by Strings a link-time
    public final HashMap<Node,String> _externals = new HashMap<>();
    public Encoding external( Node n, String extern ) {
        _externals.put(n,extern);
        return this;
    }

    // Store t as a 32/64 bit constant in the code space; generate RIP-relative
    // addressing to load it.  Type is stored in either the .rodata or .data.
    public void largeConstant( Node relo, Type t, int off, int elf ) {
        // TODO: Any-old struct in the cpool
        assert t.isConstant() || (t instanceof TypeStruct ts && Parser.startsClzPrefix(ts._name));
        assert (byte)off == off;
        assert (byte)elf == elf;
        _bigCons.put(relo,new Relo(relo,t,(byte)off,(byte)elf));
    }

    // --------------------------------------------------
    void encode( CompUnit ref ) {
        // Basic block layout: negate branches to keep blocks in-order; insert
        // unconditional jumps.  Attempt to keep backwards branches taken,
        // forwards not-taken (this is the default prediction on most
        // hardware).  Layout is still Reverse Post Order but with more
        // restrictions.
        basicBlockLayout( ref );

        // Write encoding bits in order into a big byte array.
        // Record opcode start and length.
        writeEncodings();

        // Short-form RIP-relative support: replace long encodings with short
        // encodings and compact the code, changing all the offsets.
        compactShortForm();

        // Write the constant pool
        writeConstantPool(_cpool,true );
        // Write the static memory
        writeConstantPool(_sdata,false);

        // Patch RIP-relative and local encodings now.
        patchLocalRelocations();
    }

    // --------------------------------------------------
    // Basic block layout: negate branches to keep blocks in-order; insert
    // unconditional jumps.  Attempt to keep backwards branches taken, forwards
    // not-taken (this is the default prediction on most hardware).  Layout is
    // still Reverse Post Order but with more restrictions.
    private void basicBlockLayout( CompUnit cu ) {
        IdentityHashMap<LoopNode,Ary<CFGNode>> rpos = new IdentityHashMap<>();
        _cfg = new Ary<>(CFGNode.class);
        rpos.put(_code._start.loop(),_cfg);
        BitSet visit = _code.visit();

        // Do them all except the <clinit>
        FunNode clinit=null;
        for( FunNode fun : _code._linker ) {
            if( fun != null && !fun.isDead() && fun._compunit == cu ) {
                if( fun.isClz() ) {
                    clinit = fun;
                } else {
                    int x = _cfg._len;
                    _rpo_cfg(fun,visit,rpos);
                    assert _cfg.at(x) instanceof ReturnNode;
                }
            }
        }
        // Now the <clinit> last, so when reversed it becomes first
        int x = _cfg._len;
        _rpo_cfg(clinit,visit,rpos);
        assert _cfg.at(x) instanceof ReturnNode;


        // Reverse in-place
        for( int i=0; i< _cfg.size()>>1; i++ )
            _cfg.swap(i,_cfg.size()-1-i);
        visit.clear();
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
        // bld` is true, then `fld < bld` must be false, or else both directions
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
        // Jump to a merge point, assuming other things are jumping there as well
        if( f.out(0) instanceof RegionNode ) return true ;
        if( t.out(0) instanceof RegionNode ) return false;
        // Everything else equal, use pre-order
        return t._pre > f._pre;
    }

    // Do we continue forwards from 'c' to the Loop back edge, without doing
    // anything else?  Then this branch can directly jump to the loop head.
    private static boolean forwardsEmptyScan( CFGNode c, int bld ) {
        if( c.nOuts()!=1 || c.loopDepth()!=bld ) return false;
        return c.uctrl() instanceof RegionNode cfg &&
            ((cfg instanceof LoopNode && cfg._ltree==c._ltree) || forwardsEmptyScan(cfg,bld));
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

    // Current function is used by the spill-op encodings to query the stack
    // frame layout and get the spill offsets.
    private void writeEncodings() {
        for( CFGNode bb : _cfg ) {
            if( !(bb instanceof MachNode mach0) )
                opStart(bb, _bits.size());
            else if( bb instanceof FunNode fun ) {
                opStart(bb, _bits.size());
                mach0.encoding( this );
                opLen(bb, (byte) (_bits.size() - opStart(bb)));
            }
            for( Node n : bb._outputs ) {
                if( n instanceof MachNode mach && !(n instanceof FunNode) ) {
                    opStart(n, _bits.size());
                    mach.encoding( this );
                    opLen(n, (byte) (_bits.size() - opStart(n)));
                }
            }
        }
    }

    // --------------------------------------------------
    // Short-form RIP-relative support: replace short encodings with long
    // encodings and expand the code, changing all the offsets.
    private void compactShortForm() {
        int len = _cfg._len;
        int[] oldStarts = new int[len];
        for( int i=0; i<len; i++ )
            oldStarts[i] = opStart(_cfg.at(i));

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
                CFGNode bb = _cfg.at(i);
                opStartAdd(bb, slide);
                // Slide down all other (non-CFG) ops in the block
                for( Node n : bb._outputs )
                    if( n instanceof MachNode && !(n instanceof CFGNode) )
                        opStartAdd(n, slide);
                if( bb instanceof RIPRelSize riprel ) {
                    CFGNode target = (bb instanceof IfNode iff ? iff.cproj(0) : (CFGNode)bb.out(0)).uctrlSkipEmpty();
                    // Delta is from opStart to opStart.  X86 at least counts
                    // the delta from the opEnd, but we don't have the end until
                    // we decide the size - so the encSize has to deal
                    int delta = opStart(target) - opStart(bb);
                    byte opLen = riprel.encSize(delta);
                    // Recorded size is smaller than the current size?
                    if( opLen(bb) < opLen ) {
                        // Start sliding the code down; record slide amount and new size
                        slide += opLen - opLen(bb);
                        opLen(bb, opLen);
                    }
                }
            }
        }


        // Go again, inserting padding on function headers.  Since no
        // short-jumps span function headers, the padding will not make any
        // short jumps fail.
        for( int i=0; i<len; i++ ) {
            CFGNode bb = _cfg.at(i);
            // Functions pad to align 16
            if( bb instanceof FunNode ) {
                int newStart = opStart(bb)+slide;
                slide += (newStart+15 & -16)-newStart;
            }
            opStartAdd(bb, slide);
            for( Node n : bb._outputs )
                if( n instanceof MachNode && !(n instanceof CFGNode) )
                    opStartAdd(n, slide);
        }

        // Copy/slide the bits to make space for all the longer branches
        int grow = opStart(_cfg.at(len-1)) - oldStarts[len-1];
        if( grow > 0 ) {        // If no short-form ops, nothing to do here
            int end = _bits.size();
            byte[] bits = new byte[end+grow];
            for( int i=len-1; i>=0; i-- ) {
                int start = oldStarts[i];
                if( start==0 && i>1 ) continue;
                int oldStart = oldStarts[i];
                int newStart = opStart(_cfg.at(i));
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
            int start = opStart(src);
            Node dst =  _internals.get(src);
            // If function is entirely dead, only the function pointer remains
            // and, it can only be used to test against zero or equals to
            // another function pointer... i.e., there Is No Code Here.
            int target = dst == null ? start : opStart(dst);
            ((RIPRelSize)src).patch(this, start, opLen(src), target - start);
        }
    }

    // --------------------------------------------------
    // Write the constant pool into the BAOS and optionally patch locally
    void writeConstantPool( BAOS bits, boolean ro ) {
        padN(16,bits);

        // radix sort the big constants by alignment
        Ary<Relo>[] raligns = new Ary[5];
        for( Node op : _bigCons.keySet() ) {
            Relo relo = _bigCons.get(op);
            // non-constant structs in the r/w data, everything else in r/o data
            if( (relo._t instanceof TypeStruct ts && !ts.isConstant()) == ro )
                continue;
            int align = relo._t.alignment();
            Ary<Relo> relos = raligns[align]==null ? (raligns[align]=new Ary<>(Relo.class)) : raligns[align];
            relos.add(relo);
        }


        // Types can be used more than once; collapse the dups
        HashMap<Type,Integer> targets = new HashMap<>();

        // By alignment
        for( int align = raligns.length-1; align >= 0; align-- ) {
            Ary<Relo> relos = raligns[align];
            if( relos == null ) continue;
            for( Relo relo : relos ) {
                // Map from relo to constant start and patch
                Integer target = targets.get(relo._t);
                if( target==null ) {
                    targets.put(relo._t,target = bits.size());
                    // Write constant into constant pool
                    switch( relo._t ) {
                    case TypeTuple  tt -> throw Utils.TODO("no tuples here, use structs instead");
                    case TypeStruct ts -> addStruct(bits,ts);
                    // Simple primitive (e.g. larger int, float, function ptr)
                    default -> addN(align,relo._t,bits);
                    }
                }
                // Record target address and opcode start.
                // Target is relative to the cpool/sdata start.
                relo._target = target;
                relo._opStart= opStart(relo._op);
            }
        }

    }

    // Emit a single scalar as bits
    static void addN( int log, Type t, BAOS bits ) {
        long x = switch( t ) {
        case TypeInteger ti -> ti.value();
        case TypeFloat tf -> log==3
        ? Double.doubleToRawLongBits(    tf.value())
        : Float.floatToRawIntBits((float)tf.value());
        case TypeFunPtr tfp -> 0; // These need to be relocated
        case TypeMemPtr tmp -> 0; // These need to be relocated
        default -> { if( t==Type.NIL ) yield 0; else throw Utils.TODO(); }
        };
        addN(log,x,bits);
    }

    // Structs use internal field layout
    private void addStruct( BAOS bits, TypeStruct ts ) {
        // Field order by offset
        int[] layout = ts.layout();
        int off=0; // offset in the struct
        for( int fn=0; fn<ts._fields.length; fn++ ) {
            Field f  = ts._fields[layout[fn]];
            int foff = ts. offset(layout[fn]);
            // Pad up to field
            while( off < foff ) { bits.write(0); off++; };
            // Constant array fields are special
            if( f._fname=="[]" ) {   // Must be a constant array
                ((TypeConAry)f._t).write(bits);
                off += ((TypeConAry)f._t).len();
            } else {
                int log = f._t.log_size();
                addN(log,f._t.isConstant() ? f._t : f._t.makeZero(),bits);
                off += 1<<log;
            }
        }
    }


    // A series of libc/external calls that Simple can link against in a JIT.
    // Since no runtime in the JVM process, using magic numbers for the CPU
    // emulators to pick up on.
    public static int SENTINEL_CALLOC = -4;
    public static int SENTINEL_WRITE  = -8;

    void patchGlobalRelocations() {
        for( Node src : _externals.keySet() ) {
            int start  = opStart(src);
            String dst =  _externals.get(src);
            int target = switch( dst ) {
            case "calloc" -> SENTINEL_CALLOC;
            case "write"  -> SENTINEL_WRITE ;
            default -> throw Utils.TODO();
            };
            ((RIPRelSize)src).patch(this, start, opLen(src), target - start);
        }
    }
}
