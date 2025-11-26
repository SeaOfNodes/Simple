package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.ArrayList;

/**
 * Integer Type
 */
public class TypeInteger extends Type {
    /**
     * Describes an integer *range* - everything from min to max; both min and
     * max are inclusive.  If min==max, this is a constant.
     * <br>
     * If min <= max, this is a  below center (towards bottom).
     * If min >  max, this is an above center (towards top).
     */
    public long _min, _max;

    public byte _widen;

    private static final Ary<TypeInteger> FREE = new Ary<>(TypeInteger.class);
    private TypeInteger(long min, long max, byte widen) { super(TINT); init(min,max,widen); }
    private TypeInteger init(long min, long max, byte widen) {
        // Constants must always have a widen value of zero.  Widening is only
        // meaningful for ranges of values - which otherwide can increase in
        // range size indefinitely.
        assert min!=max || widen==0; // Constants always zero widen
        _min = min; _max = max; _widen=widen;
        return this;
    }
    public static TypeInteger malloc(long lo, long hi, byte widen) { return FREE.isEmpty() ? new TypeInteger(lo,hi,widen) : FREE.pop().init(lo,hi,widen); }
    public static TypeInteger make(long lo, long hi) { return make(lo,hi,(byte)0); }
    public static TypeInteger make(long lo, long hi, byte widen) {
        TypeInteger i = malloc(lo,hi,lo==hi ? 0 : widen);
        TypeInteger t2 = i.intern();
        return t2==i ? i : t2.free(i);
    }
    public TypeInteger makeWide(byte widen) {
        if( widen == _widen ) return this;
        if( _min == Long.MIN_VALUE && _max == Long.MAX_VALUE )
            return TypeInteger.BOT;
        if( _min==_max ) return this; // Leave constants always narrow
        return make(_min,_max,widen);
    }
    @Override TypeInteger free(Type t) {
        TypeInteger i = (TypeInteger)t;
        i._min = i._max = 0;
        i._widen = 0;
        i._hash = 0;
        i._dual = null;
        FREE.push(i);
        return this;
    }

    public Type same_but_slightly_wider_than( Type minType ) {
        return _widen < 3 ? make(_min,_max, (byte)(_widen+1)) : minType;
    }
    // Force max widen
    @Override public Type widen() {
        return _widen==3 ? this : make(_min,_max,(byte)3);
    }
    public static TypeInteger constant(long con) { return make(con, con); }

    public final static TypeInteger ZERO= make(0,0);
    public final static TypeInteger FALSE=ZERO;
    public final static TypeInteger TRUE= make(1,1);
    public final static TypeInteger TWO = make(2,2); // Shows up in some Simple tests as the starting argument

    public final static TypeInteger I1  = make(-1,0,(byte)3);
    public final static TypeInteger I8  = make(-128,127,(byte)3);
    public final static TypeInteger I16 = make(-32768,32767,(byte)3);
    public final static TypeInteger I32 = make(-1L<<31,(1L<<31)-1,(byte)3);
    public final static TypeInteger BOT = make(Long.MIN_VALUE,Long.MAX_VALUE,(byte)3);
    public final static TypeInteger TOP = (TypeInteger)BOT.dual();

    public final static TypeInteger U1  = make(0,1,(byte)3);
    public final static TypeInteger BOOL= U1;
    public final static TypeInteger U8  = make(0,255,(byte)3);
    public final static TypeInteger U16 = make(0,65535,(byte)3);
    public final static TypeInteger U32 = make(0,(1L<<32)-1,(byte)3);

    public final static TypeInteger FATBOOL = make(0,1,(byte)2);

    public static void gather(ArrayList<Type> ts) { ts.add(I32); ts.add(BOT); ts.add(U1); ts.add(I1); ts.add(U8); ts.add(ZERO); ts.add(FATBOOL); }

    @Override public String str() {
        if( _isConstant() ) return ""+_min;
        long lo = _min, hi = _max;
        String x = "";
        if( hi < lo ) {
            lo = _max;  hi = _min;
            x = "~";
        }
        return x+_str(lo,hi);
    }
    private static String _str(long lo, long hi) {
        if( lo==Long.MIN_VALUE && hi==Long.MAX_VALUE ) return "i64";
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

    @Override public int log_size() {
        if( isHigh() ) return 0; // High types are dead, and should never hit code emission.
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
        if( -128 <= _min && _max < 128 ) return 0; // 1 byte
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
        if( other instanceof TypeConAry ary ) return ary.imeet(this);
        // Invariant from caller: 'this' != 'other' and same class (TypeInteger)
        TypeInteger i = (TypeInteger)other; // Contract
        return make(Math.min(_min,i._min), Math.max(_max,i._max), (byte)Math.max(_widen,i._widen));
    }

    @Override TypeInteger xdual() {
        return _min==_max ? this : malloc(_max,_min,(byte)(-_widen));
    }

    @Override public TypeInteger nonZero() {
        if( isHigh() ) return this;
        if( this==ZERO ) return null;                  // No sane answer
        if( _min==0 ) return make(1,Math.max(_max,1)); // specifically good on BOOL
        if( _max==0 ) return make(_min,-1);
        return this;
    }
    @Override public Type makeZero() { return ZERO; }
    @Override boolean _isConstant() { return _min == _max; }
    @Override boolean _isGLB(boolean mem) { return _glb(mem)==this; }
    @Override Type _glb(boolean mem) {
        if( !mem ) return BOT;
        // GLB is not well-defined in memory; depends on the size of the memory field
        if( _isConstant() ) return BOT;
        return isHigh() ? dual() : this;
    }

    @Override int hash() { return Utils.fold(_min) * Utils.fold(_max) + _widen; }
    @Override public boolean eq( Type t ) {
        return t instanceof TypeInteger i && _min==i._min && _max==i._max && _widen==i._widen;
    }
}
