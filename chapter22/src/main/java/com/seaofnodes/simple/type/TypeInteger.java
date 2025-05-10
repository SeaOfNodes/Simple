package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger ZERO= make(0,0);
    public final static TypeInteger FALSE=ZERO;
    public final static TypeInteger TRUE= make(1,1);

    public final static TypeInteger I1  = make(-1,0);
    public final static TypeInteger I8  = make(-128,127);
    public final static TypeInteger I16 = make(-32768,32767);
    public final static TypeInteger I32 = make(-1L<<31,(1L<<31)-1);
    public final static TypeInteger BOT = make(Long.MIN_VALUE,Long.MAX_VALUE);
    public final static TypeInteger TOP = BOT.dual();

    public final static TypeInteger U1  = make(0,1);
    public final static TypeInteger BOOL= U1;
    public final static TypeInteger U8  = make(0,255);
    public final static TypeInteger U16 = make(0,65535);
    public final static TypeInteger U32 = make(0,(1L<<32)-1);

    /**
     * Describes an integer *range* - everything from min to max; both min and
     * max are inclusive.  If min==max, this is a constant.
     * <br>
     * If min <= max, this is a  below center (towards bottom).
     * If min >  max, this is an above center (towards top).
     */
    public final long _min, _max;

    private TypeInteger(long min, long max) {
        super(TINT);
        _min = min;
        _max = max;
    }

    // Strict non-zero contract
    public static TypeInteger make(long lo, long hi) { return new TypeInteger(lo,hi).intern(); }

    public static TypeInteger constant(long con) { return make(con, con); }

    public static void gather(ArrayList<Type> ts) { ts.add(I32); ts.add(BOT); ts.add(U1); ts.add(I1); ts.add(U8); }

    @Override public String str() {
        if( isConstant() ) return ""+_min;
        long lo = _min, hi = _max;
        String x = "";
        if( hi < lo ) {
            lo = _max;  hi = _min;
            x = "~";
        }
        return x+_str(lo,hi);
    }
    private static String _str(long lo, long hi) {
        if( lo==Long.MIN_VALUE && hi==Long.MAX_VALUE ) return "int";
        if( lo==       0 && hi==         1 ) return "bool";
        if( lo==      -1 && hi==         0 ) return "i1";
        if( lo==    -128 && hi==       127 ) return "i8";
        if( lo==  -32768 && hi==     32767 ) return "i16";
        if( lo== -1L<<31 && hi==(1L<<31)-1 ) return "i32";
        if( lo==       0 && hi==      255  ) return "u8";
        if( lo==       0 && hi==     65535 ) return "u16";
        if( lo==       0 && hi==(1L<<32)-1 ) return "u32";
        return "["+lo+"-"+hi+"]";
    }

    @Override public boolean isHigh    () { return _min >  _max; }
    @Override public boolean isConstant() { return _min == _max; }

    @Override public int log_size() {
        assert !isHigh(); // High types are dead, and should never hit code emission.
        if( this==I8  || this==U8 || this==BOOL ) return 0; // 1<<0 == 1 bytes
        if( this==I16 || this==U16              ) return 1; // 1<<1 == 2 bytes
        if( this==I32 || this==U32              ) return 2; // 1<<2 == 4 bytes
        if( this==BOT                           ) return 3; // 1<<3 == 8 bytes
        if( isConstant() ) {                                // just const here
            if (-0xFF <= _min && _min <= 0xFF)                    return 0;
            else if (-0xFFFF <= _min && _min <= 0xFFFF)           return 1;
            else if (-0xFFFFFFFFL <= _min && _min <= 0xFFFFFFFFL) return 2;
            else return 3;
        }
        throw Utils.TODO();
    }

    public long value() { assert isConstant(); return _min; }

    // AND-mask of forced zeros.  e.g. unsigned types will return their mask;
    // u8 will return 0xFF.  But also a range of 16-18 (0x10-0x12) will return
    // 0x13 - no value in the range {16,17,18} will allow bit 0x04 to be set.
    public long mask() {
        if( isHigh() ) return 0;
        if( isConstant() ) return _min;
        // Those bit positions which differ min to max
        long x = _min ^ _max;
        // Highest '1' bit in the differ set.  Since the range is from min to
        // max, all values below here are possible.
        long ff1 = Long.highestOneBit(x);
        // Make a all-1's mask from ff1, and set over the same bits (either min
        // or max is ok).
        long mask = _min | (ff1-1) | ff1;
        return mask;
    }

    @Override
    public Type xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeInteger)
        TypeInteger i = (TypeInteger)other; // Contract
        return make(Math.min(_min,i._min), Math.max(_max,i._max));
    }

    @Override public TypeInteger dual() { return make(_max,_min); }

    @Override public TypeInteger nonZero() {
        if( isHigh() ) return this;
        if( this==ZERO ) return null;                  // No sane answer
        if( _min==0 ) return make(1,Math.max(_max,1)); // specifically good on BOOL
        if( _max==0 ) return make(_min,-1);
        return this;
    }
    @Override public Type makeZero() { return ZERO; }
    @Override public Type glb(boolean mem) { return mem ? (isHigh() ? dual() : this) : BOT; }
    @Override int hash() { return Utils.fold(_min) * Utils.fold(_max); }
    @Override public boolean eq( Type t ) {
        TypeInteger i = (TypeInteger)t; // Contract
        return _min==i._min && _max==i._max;
    }
}
