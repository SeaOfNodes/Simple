package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.SB;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * Values that fit in a register or field: integers, floats and pointers.
 * These bounds exclude control and memory. An unresolved load can produce a
 * scalar without making the parser guess which kind of value it will hold.
 */
public class TypeScalar extends Type {
    protected TypeScalar(byte type) { super(type); }
    private TypeScalar() { this(TSCALAR); }
    public static final TypeScalar BOT = new TypeScalar().intern();
    public static final TypeScalar TOP = BOT.dual();
    public static void gather(ArrayList<Type> ts) { ts.add(BOT); }

    final Type smeet(TypeScalar scalar) {
        if( _type==TSCALAR ) return isHigh() ? scalar : this;
        if( scalar._type==TSCALAR ) return scalar.isHigh() ? this : scalar;
        return BOT;
    }
    @Override Type xmeet(Type t) { return isHigh() ? t : this; }
    @Override TypeScalar xdual() { return new TypeScalar(); }
    @Override public boolean isHigh() { return this==TOP; }
    @Override boolean _isGLB(boolean mem) { return this==BOT; }
    @Override Type _glb(boolean mem) { return BOT; }
    @Override public String str() { return isHigh() ? "~scalar" : "scalar"; }
    @Override SB _print(SB sb, BitSet visit, boolean html) { return sb.p(str()); }
}
