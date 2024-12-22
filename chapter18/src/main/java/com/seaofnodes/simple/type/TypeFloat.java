package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import java.util.ArrayList;

/**
 * Float Type
 */
public class TypeFloat extends TypeNil {

    public final static TypeFloat  ONE = constant(1.0);
    public final static TypeFloat  F32 = make((byte)3, true, 0);
    public final static TypeFloat NF64 = make((byte)2,false, 0);
    public final static TypeFloat  F64 = make((byte)3,false, 0);

    // true for 32bits, false for 64bits
    public final boolean _f32;

    /**
     * The constant value or 0
     */
    public final double _con;

    private TypeFloat(byte nil, boolean f32, double con) {
        super(TFLT,nil);
        assert !isConstant() || ((float)con == con) == f32;
        _f32 = f32;
        _con = con;
    }
    private static TypeFloat make(byte nil, boolean f32, double con) {
        return new TypeFloat(nil,f32,con).intern();
    }
    @Override public TypeFloat makeFrom(byte nil) {
        return nil==_nil ? this : make(nil,_f32,_con);
    }

    public static TypeFloat constant(double con) { assert con!=0; return make((byte)2,((float)con)==con, con); }

    public static void gather(ArrayList<Type> ts) { ts.add(F64); ts.add(F32); ts.add(constant(3.141592653589793)); }

    @Override public String str() {
        if( isConstant() )
            return ""+_con+(_f32 ? "f" : "");
        String s = "";
        if( isHigh() ) s = "~";
        if( 1 <= _nil && _nil <= 2 ) // Disallow nil?
            s += "n";
        s += "f";
        s += _f32 ? "32" : "lt";
        return s;
    }

    @Override public boolean isConstant() { return _con!=0; }
    @Override public int log_size() { return _f32 ? 2 : 3; }

    public double value() { return _con; }

    @Override
    public TypeFloat xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeFloat).
        TypeFloat i = (TypeFloat)other; // Contract
        if(   isConstant() && i.isHigh() ) return i._f32 && !  _f32 ? NF64 : this;
        if( i.isConstant() &&   isHigh() ) return   _f32 && !i._f32 ? NF64 :  i  ;


        // From online Karnaugh map; if both values are small the result is
        // small, otherwise the result is small if the otherside is high.
        boolean f32 = (_f32&i._f32) | (i.isHigh()&_f32) | (isHigh()&i._f32);
        return make(xmeet0(i), f32, 0);
    }

    @Override
    public Type dual() {
        if( isConstant() ) return this; // Constants are a self-dual
        return make(dual0(),_f32,0);
    }

    // RHS is NIL
    @Override Type meet0() {
        // From high, falling to the least Float that contains a zero
        return isHigh() || _f32 ? F32 : F64;
    }


    @Override public Type glb() { return F64; }

    @Override
    int hash() { return (int)(Double.hashCode(_con) ^ (_f32 ? 2048 : 0) ^ super.hash()); }
    @Override
    public boolean eq( Type t ) {
        TypeFloat i = (TypeFloat)t; // Contract
        return _con==i._con && _f32==i._f32 && super.eq(i);
    }

}
