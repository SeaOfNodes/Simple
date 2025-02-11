package com.seaofnodes.simple;

import java.util.BitSet;

// A "register mask" - 1 bit set for each allowed register.  In addition "stack
// slot" registers may be allowed, effectively making the set infinite.
public class RegMask {

    public static final RegMask EMPTY = new RegMask();

    final BitSet _bits;

    public RegMask(long   x ) { _bits = BitSet.valueOf(new long[]{x}); }
    public RegMask(long[] xs) { _bits = BitSet.valueOf(           xs); }

    public RegMask or(RegMask other) {
        BitSet resultBits = (BitSet)this._bits.clone();
        resultBits.or(other._bits);
        return new RegMask(resultBits);
    }

    // Internal constructor
    RegMask() { _bits = new BitSet(); }
    RegMask(BitSet bs) { _bits = bs; }

    public boolean get(int r) { return _bits.get(r); }

    // Defensive writable copy
    public RegMaskRW copy() { return new RegMaskRW( (BitSet)_bits.clone() ); }

    boolean size1() { return _bits.cardinality()==1; }
}

class RegMaskRW extends RegMask {
    public RegMaskRW() { super(); }
    public RegMaskRW(long x) { super(new long[]{x}); }
    public RegMaskRW(long[] xs) { super(xs); }
    public RegMaskRW(BitSet bs) { super(bs); }
    public void set(int r) { _bits.set(r); }
    public void clr(int r) { _bits.clear(r); }
    public void set(int r, boolean b) { _bits.set(r,b); }
    public RegMaskRW and( RegMask r ) {
        _bits.and(r._bits);
        return this;
    }
}
