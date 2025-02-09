package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.SB;

// A "register mask" - 1 bit set for each allowed register.  In addition "stack
// slot" registers may be allowed, effectively making the set infinite.
public class RegMask {

    final long _bits;

    private static final RegMask EMPTY = new RegMask(0);
    public static final RegMask FULL = new RegMask(-1L);

    public RegMask(long x ) { _bits = x; }
    public RegMask(long[] xs) { /*_bits = BitSet.valueOf(           xs); */ throw Utils.TODO(); }
    // Internal constructor
    RegMask() { /*_bits = new BitSet();*/ _bits = 0; }
    //RegMask(BitSet bs) { _bits = bs; }

    // Copy-on-write
    RegMask and( RegMask mask ) {
        if( mask==null ) return this;
        long bits = _bits & mask._bits;
        if( bits==_bits ) return this;
        if( bits==mask._bits ) return mask;
        if( bits==0 ) return EMPTY;
        // Update-in-place a mutable mask, or make a defensive copy
        throw Utils.TODO();
    }

    boolean isEmpty() { return _bits==0; }

    // Defensive writable copy
    public RegMaskRW copy() { return new RegMaskRW( /*(BitSet)_bits.clone()*/ ); }

    boolean size1() { return (_bits & ~(_bits-1))==_bits; }

    @Override public String toString() { return toString(new SB()).toString(); }
    public SB toString(SB sb) {
        Machine mach = CodeGen.CODE._mach;
        if( _bits==0 ) return sb.p("[]");
        sb.p("[");
        for( int i=0; i<64; i++ )
            if( ((_bits >> i)&1) != 0 )
                sb.p(mach.reg(i)).p(",");
        return sb.unchar().p("]");
    }
}

class RegMaskRW extends RegMask {
    public RegMaskRW() { super(); }
    public RegMaskRW(long x) { super(new long[]{x}); }
    public RegMaskRW(long[] xs) { super(xs); }
    //public RegMaskRW(BitSet bs) { super(bs); }
    public void set(int r) { /*_bits.set(r);*/ }
    public void clr(int r) { /*_bits.clear(r);*/ }
    public void set(int r, boolean b) { /*_bits.set(r,b);*/ }
    public RegMaskRW and( RegMask r ) {
        //_bits.and(r._bits);
        return this;
    }
    boolean isEmpty() { throw Utils.TODO(); }
}
