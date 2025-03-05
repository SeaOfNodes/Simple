package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.SB;

// A "register mask" - 1 bit set for each allowed register.  In addition "stack
// slot" registers may be allowed, effectively making the set infinite.
public class RegMask {

    long _bits0, _bits1;

    private static final RegMask EMPTY = new RegMask(0L);
    public  static final RegMask FULL = new RegMask(-1L);

    public RegMask(int bit) {
        if( bit < 64 ) _bits0 = 1L<<bit;
        else _bits1 = 1L<<(bit=64);
    }
    public RegMask(long bits ) { _bits0 = bits; }
    public RegMask(long bits0, long bits1 ) { _bits0 = bits0; _bits1 = bits1; }
    private RegMask() { _bits0 = _bits1 = 0; }

    // AND, with copy-on-write if changing
    RegMask and( RegMask mask ) {
        if( mask==null ) return this;
        long bits0 = _bits0 & mask._bits0;
        long bits1 = _bits1 & mask._bits1;
        if( bits0==_bits0 && bits1==_bits1 ) return this;
        if( bits0==mask._bits0 && bits1==mask._bits1 ) return mask;
        if( bits0==0 && bits1==0 ) return EMPTY;
        // Update-in-place a mutable mask, or make a defensive copy
        return null;
    }
    RegMask sub( RegMask mask ) {
        if( mask==null ) return this;
        long bits0 = _bits0 & ~mask._bits0;
        long bits1 = _bits1 & ~mask._bits1;
        if( bits0==_bits0 && bits1==_bits1 ) return this;
        if( bits0==mask._bits0 && bits1==mask._bits1 ) return mask;
        if( bits0==0 && bits1==0 ) return EMPTY;
        // Update-in-place a mutable mask, or make a defensive copy
        return null;
    }

    // Fails if bit is set, because this is immutable
    public boolean clr( int reg ) {
        if( reg <  64 ) return ((_bits0 >> reg)&1)==0;
        if( reg < 128 ) return ((_bits1 >> reg)&1)==0;
        return true;
    }


    public short firstReg() {
        return (short)(_bits0 != 0
                       ? Long.numberOfTrailingZeros(_bits0)
                       : Long.numberOfTrailingZeros(_bits1)+64);
    }
    public short nextReg(short reg) {
        if( reg<64 ) {
            long bits0 = _bits0 >> (reg+1);
            if( bits0!=0 )
                return (short)(reg+1+Long.numberOfTrailingZeros(bits0));
            reg=64;
        }
        long bits1 = _bits1 >> (reg-64+1);
        if( bits1!=0 )
            return (short)(reg+1+Long.numberOfTrailingZeros(bits1));
        return -1;
    }

    boolean isEmpty() { return _bits0==0 && _bits1==0; }

    boolean test( int reg ) {
        return ((reg<64 ? (_bits0 >> reg) : (_bits1 >> (reg-64))) & 1)  != 0;
    }

    // checks if the 2 masks have at least 1 bit in common
    public boolean overlap( RegMask mask ) { return (_bits0 & mask._bits0)!=0 || (_bits1 & mask._bits1)!=0; }

    // Defensive writable copy
    public RegMaskRW copy() { return new RegMaskRW( _bits0, _bits1 ); }

    // Has exactly 1 bit set
    public boolean size1() {
        return ((_bits0 & -_bits0)==_bits0 && _bits1==0) ||
               ((_bits1 & -_bits1)==_bits1 && _bits0==0);
    }

    // Cardinality
    public short size() { return (short)(Long.bitCount(_bits0)+Long.bitCount(_bits1)); }

    @Override public String toString() { return toString(new SB()).toString(); }
    public SB toString(SB sb) {
        Machine mach = CodeGen.CODE._mach;
        if( _bits0==0 && _bits1==0 ) return sb.p("[]");
        sb.p("[");
        for( int i=0; i<64; i++ )
            if( ((_bits0 >> i)&1) != 0 )
                sb.p(mach.reg(i)).p(",");
        for( int i=0; i<64; i++ )
            if( ((_bits1 >> i)&1) != 0 )
                sb.p(mach.reg(i+64)).p(",");
        return sb.unchar().p("]");
    }
}

// Mutable regmask(writable)
class RegMaskRW extends RegMask {
    public RegMaskRW(long x, long y) { super(x,y);  }
    // clears bit at position r. Returns true if the mask is still not empty.
    public boolean clr(int r) {
        if( r < 64 ) _bits0 &= ~(1L<<(r   ));
        else         _bits1 &= ~(1L<<(r-64));
        return _bits0!=0 || _bits1!=0;
    }
    @Override RegMaskRW and( RegMask mask ) {
        if( mask==null ) return this;
        _bits0 &= mask._bits0;
        _bits1 &= mask._bits1;
        return this;
    }
    @Override RegMaskRW sub( RegMask mask ) {
        if( mask==null ) return this;
        _bits0 &= ~mask._bits0;
        _bits1 &= ~mask._bits1;
        return this;
    }
}
