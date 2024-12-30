package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Float Type
 */
public class TypeFloat extends Type {

    public final static TypeFloat ONE = constant(1.0);
    public final static TypeFloat FZERO = constant(0.0);
    public final static TypeFloat F32 = make(32, 0);
    public final static TypeFloat F64 = make(64, 0);

    // - high -64, high -32, con 0, low +32, low +64
    public final byte _sz;

    /**
     * The constant value or 0
     */
    public final double _con;

    private TypeFloat(byte sz, double con) {
        super(TFLT);
        _sz = sz;
        _con = con;
    }
    private static TypeFloat make(int sz, double con) {
        return new TypeFloat((byte)sz,con).intern();
    }

    public static TypeFloat constant(double con) { return make(0, con); }

    public static void gather(ArrayList<Type> ts) { ts.add(F64); ts.add(F32); ts.add(constant(3.141592653589793)); }

    @Override public String str() {
        return switch( _sz ) {
        case -64 -> "~flt";
        case -32 -> "~f32";
        case   0 -> ""+_con+((float)_con==_con ? "f" : "");
        case  32 ->  "f32";
        case  64 ->  "flt";
        default  -> throw Utils.TODO();
        };
    }
    private boolean isF32() { return ((float)_con)==_con; }

    @Override public boolean isConstant() { return _sz==0; }
    @Override public int log_size() { return _sz==32 || _sz==-32 ? 2 : 3; }

    public double value() { return _con; }

    @Override
    public TypeFloat xmeet(Type other) {
        TypeFloat f = (TypeFloat)other;
        // Invariant from caller: 'this' != 'other' and same class (TypeFloat).
        TypeFloat i = (TypeFloat)other; // Contract
        // Larger size in i1, smaller in i0
        TypeFloat i0 = _sz < i._sz ? this : i;
        TypeFloat i1 = _sz < i._sz ? i : this;

        if( i1._sz== 64 ) return F64;
        if( i0._sz==-64 ) return i1;
        if( i1._sz== 32 )
            return i0._sz==0 && !i0.isF32() ? F64 : F32;
        if( i1._sz!=  0 ) return i1;
        // i1 is a constant
        if( i0._sz==-32 )
            return i1.isF32() ? i1 : F64;
        // Since both are constants, and are never equals (contract) unequals
        // constants fall to bottom
        return i0.isF32() && i1.isF32() ? F32 : F64;
    }

    @Override
    public Type dual() {
        return isConstant() ? this : make(-_sz,0); // Constants are a self-dual
    }

    @Override public Type glb() { return F64; }

    @Override
    int hash() { return (int)(Double.hashCode(_con) ^ _sz ^ (1<<17)); }
    @Override
    public boolean eq( Type t ) {
        TypeFloat i = (TypeFloat)t; // Contract
        return _con==i._con && _sz==i._sz;
    }

}
