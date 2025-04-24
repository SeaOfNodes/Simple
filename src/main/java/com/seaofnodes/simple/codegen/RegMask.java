package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.SB;

/** RegMask
 *  A "register mask" - 1 bit set for each allowed register.  In addition,
 *  "stack slot" registers may be allowed, effectively making the set infinite.
 * <p>
 *  For smaller and simpler machines it suffices to make such masks an i64 or
 *  i128 (64- or 128-bit integers), and this presentation is by far the better
 *  way to go... if all register allocations can fit in this bit limitation.
 *  The allocator will need bits for stack-based parameters and for splits
 *  which cannot get a register.  For a 32-register machine like the X86, add 1
 *  for flags - gives 33 registers.  Using a Java `long` has 64 bits, leaving
 *  31 for spills and stack passing.  This is adequate for nearly all
 *  allocations; only the largest allocations will run this out.  However, if
 *  we move to a chip with 64 registers we'll immediately run out, and need at
 *  least a 128 bit mask.  Since you cannot *return* a 128 bit value directly
 *  in Java, Simple will pick up a `RegMask` class object.
*/
public class RegMask {

    long _bits0, _bits1;

    private static final RegMask EMPTY = new RegMask(0L);
    public  static final RegMask FULL = new RegMask(-1L);

    public RegMask(int bit) {
        if( bit < 64 ) _bits0 = 1L<<bit;
        else _bits1 = 1L<<(bit-64);
    }
    public RegMask(long bits ) { _bits0 = bits; }
    public RegMask(long bits0, long bits1 ) { _bits0 = bits0; _bits1 = bits1; }
    public RegMask(RegMask r) { _bits0 = r._bits0; _bits1 = r._bits1; }
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
        if( isEmpty() ) return -1;
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

    public boolean test( int reg ) {
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
        String[] regs = mach.regs();
        for( int i=0; i<64; i++ )
            if( ((_bits0 >> i)&1) != 0 )
                sb.p(reg(regs,i)).p(",");
        for( int i=0; i<64; i++ )
            if( ((_bits1 >> i)&1) != 0 )
                sb.p(reg(regs,i+64)).p(",");
        return sb.unchar().p("]");
    }

    public static String reg(String[] regs, int i) {
        return i<regs.length ? regs[i] : "[stk#"+i+"]";
    }
}
