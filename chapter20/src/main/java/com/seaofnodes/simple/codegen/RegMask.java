package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.SB;

// A "register mask" - 1 bit set for each allowed register.  In addition "stack
// slot" registers may be allowed, effectively making the set infinite.
public class RegMask {

    long _bits;

    private static final RegMask EMPTY = new RegMask(0);
    public static final RegMask FULL = new RegMask(-1L);

    public RegMask(long x ) { _bits = x; }
    private RegMask() { _bits = 0; }

    // AND, with copy-on-write if changing
    RegMask and( RegMask mask ) {
        if( mask==null ) return this;
        long bits = _bits & mask._bits;
        if( bits==_bits ) return this;
        if( bits==mask._bits ) return mask;
        if( bits==0 ) return EMPTY;
        // Update-in-place a mutable mask, or make a defensive copy
        return null;
    }
    RegMask sub( RegMask mask ) {
        if( mask==null ) return this;
        long bits = _bits & ~mask._bits;
        if( bits==_bits ) return this;
        if( bits==mask._bits ) return mask;
        if( bits==0 ) return EMPTY;
        // Update-in-place a mutable mask, or make a defensive copy
        return null;
    }

    // Fails if bit is set, because this is immutable
    public boolean clr( int reg ) {
        return ((_bits >> reg)&1)==0;
    }


    short firstColor() {
        return (short)Long.numberOfTrailingZeros(_bits);
    }

    boolean isEmpty() { return _bits==0; }

    boolean test( int reg ) { return ((_bits >> reg)&1) != 0; }

    // checks if the 2 masks have at least 1 bit in common
    boolean overlap( RegMask mask ) { return (_bits & mask._bits)!=0;  }

    // Defensive writable copy
    public RegMaskRW copy() { return new RegMaskRW( _bits ); }

    // Has exactly 1 bit set
    boolean size1() { return (_bits & -_bits)==_bits; }

    // Cardinality
    int size() { return Long.bitCount(_bits); }

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

// Mutable regmask(writable)
class RegMaskRW extends RegMask {
    public RegMaskRW(long x) { super(x);  }
    // clears bit at position r. Returns true if the mask is still not empty.
    public boolean clr(int r) { _bits &= ~(1L<<r); return _bits!=0; }
    @Override RegMaskRW and( RegMask mask ) {
        if( mask==null ) return this;
        _bits &= mask._bits;
        return this;
    }
    @Override RegMaskRW sub( RegMask mask ) {
        if( mask==null ) return this;
        _bits &= ~mask._bits;
        return this;
    }
}
