package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.util.Ary;
import java.util.ArrayList;

/**
 * Float Type
 */
public class TypeFloat extends Type {

    // - high -64, high -32, con 0, low +32, low +64
    public byte _sz;

    /**
     * The constant value or 0
     */
    public double _con;

    private static final Ary<TypeFloat> FREE = new Ary<>(TypeFloat.class);
    private TypeFloat(byte sz, double con) { super(TFLT); init(sz,con); }
    private TypeFloat init( byte sz, double con ) { _sz = sz; _con = con; return this; }
    public static TypeFloat malloc(byte sz, double con) { return FREE.isEmpty() ? new TypeFloat(sz,con) : FREE.pop().init(sz,con); }
    private static TypeFloat make(int sz, double con) {
        TypeFloat f  = malloc((byte)sz,con);
        TypeFloat t2 = f.intern();
        return t2==f ? f : t2.free(f);
    }
    @Override TypeFloat free(Type t) {
        TypeFloat f = (TypeFloat)t;
        f._hash = 0;
        f._dual=null;
        FREE.push(f);
        return this;
    }

    public static TypeFloat constant(double con) { return make(0, con); }

    public final static TypeFloat ONE = constant(1.0);
    public final static TypeFloat FZERO = constant(0.0);
    public final static TypeFloat F32 = make(32, 0);
    public final static TypeFloat F64 = make(64, 0);

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

    @Override public boolean isHigh    () { return _sz< 0; }
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
    TypeFloat xdual() {
        return _isConstant() ? this : new TypeFloat((byte)-_sz,0); // Constants are a self-dual
    }

    @Override boolean _isGLB(boolean mem) { return _glb(mem)==this; }
    @Override Type _glb(boolean mem) {
        if( !mem ) return F64;
        if( _isConstant() ) return isF32() ? F32 : F64;
        return isHigh() ? dual() : this;
    }

    @Override boolean _isConstant() { return _sz==0; }

    @Override public Type makeZero() { return FZERO; }
    @Override
    int hash() { return (int)(Double.hashCode(_con) ^ _sz ^ (1<<17)); }
    @Override
    public boolean eq( Type t ) {
        TypeFloat i = (TypeFloat)t; // Contract
        // Allow NaN to check for equality
        return _sz==i._sz && Double.doubleToLongBits(_con)==Double.doubleToLongBits(i._con);
    }

}
