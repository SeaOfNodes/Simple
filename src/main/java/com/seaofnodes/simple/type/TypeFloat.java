package com.seaofnodes.simple.type;

import java.util.ArrayList;

/**
 * Float Type
 */
public class TypeFloat extends Type {

    public final static TypeFloat TOP = make((byte)-64, 0);
    public final static TypeFloat T32 = make((byte)-32, 0);
    public final static TypeFloat ZERO= make((byte)  0, 0);
    public final static TypeFloat B32 = make((byte) 32, 0);
    public final static TypeFloat BOT = make((byte) 64, 0);

    /** +/-64 for double; +/-32 for float, or 0 for constants */
    public final byte _sz;

    /**
     * The constant value or 0
     */
    public final double _con;

    private TypeFloat(byte sz, double con) {
        super(TFLT);
        _sz  = sz ;
        _con = con;
    }
    private static TypeFloat make(byte sz, double con) {
        return new TypeFloat(sz,con).intern();
    }

    public static TypeFloat constant(double con) { return make((byte)0, con); }

    public static void gather(ArrayList<Type> ts) { ts.add(ZERO); ts.add(BOT); ts.add(B32); ts.add(constant(3.141592653589793)); }

    // FIXME this display format is problematic
    // In visualizer '#' gets prepended if its a constant
    @Override
    public StringBuilder print(StringBuilder sb) {
        if( this==TOP ) return sb.append("FltTop");
        if( this==T32 ) return sb.append("F32Top");
        if( this==B32 ) return sb.append("F32Bot");
        if( this==BOT ) return sb.append("FltBot");
        return sb.append(_con);
    }

    @Override public String str() {
        if( this==TOP ) return "~flt";
        if( this==T32 ) return "~f32";
        if( this==B32 ) return  "f32";
        if( this==BOT ) return  "flt";
        return ""+_con+(isF32() ? "f" : "");
    }

    /**
     * Display Type name in a format that's good for IR printer
     */
    @Override
    public StringBuilder typeName(StringBuilder sb) {
        if( this==TOP ) return sb.append("FltTop");
        if( this==T32 ) return sb.append("F32Top");
        if( this==B32 ) return sb.append("F32Bot");
        if( this==BOT ) return sb.append("FltBot");
        return sb.append(isF32() ? "F32" : "Flt");
    }

    boolean isF32() { return ((float)_con)==_con; }
    @Override public boolean isHigh()        { return _sz< 0; }
    @Override public boolean isHighOrConst() { return _sz<=0; }
    @Override public boolean isConstant()    { return _sz==0; }

    public double value() { return _con; }

    @Override
    public Type xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeFloat)
        TypeFloat i = (TypeFloat)other; // Contract
        // Larger size in i1, smaller in i0
        TypeFloat i0 = _sz < i._sz ? this : i;
        TypeFloat i1 = _sz < i._sz ? i : this;

        if( i1._sz== 64 ) return BOT;
        if( i0._sz==-64 ) return i1;
        if( i1._sz== 32 )
            return i0._sz==0 && !i0.isF32() ? BOT : B32;
        if( i1._sz!=  0 ) return i1;
        // i1 is a constant
        if( i0._sz==-32 )
            return i1.isF32() ? i1 : BOT;
        // Since both are constants, and are never equals (contract) unequals
        // constants fall to bottom
        return i0.isF32() && i1.isF32() ? B32 : BOT;
    }

    @Override
    public Type dual() {
        if( _sz==0 ) return this; // Constants are a self-dual
        return make((byte)-_sz,0);
    }

    @Override
    public Type glb() { return BOT; }

    @Override public TypeFloat makeInit() { return ZERO; }

    @Override
    int hash() { return (int)(Double.hashCode(_con) ^ _sz); }
    @Override
    public boolean eq( Type t ) {
        TypeFloat i = (TypeFloat)t; // Contract
        return _con==i._con && _sz==i._sz;
    }
}
