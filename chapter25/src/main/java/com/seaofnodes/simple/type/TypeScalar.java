package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

/**
 * The envelope for values which can live in an ordinary register or memory
 * field: integers, floats, memory pointers, and function pointers.
 *
 * `BOT` means any scalar value and `TOP` means no scalar value.  They sit
 * strictly inside the global {@link Type#BOTTOM}/{@link Type#TOP} pair, which
 * also includes non-values such as control and memory.
 */
public class TypeScalar extends Type {
    protected TypeScalar(byte type) { super(type); }
    private TypeScalar() { this(TSCALAR); }

    public static final TypeScalar BOT = new TypeScalar().intern();
    public static final TypeScalar TOP = BOT.dual();

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); }

    /** Meet two different scalar families. */
    final Type smeet(TypeScalar scalar) {
        if( _type == TSCALAR ) return isHigh() ? scalar : this;
        if( scalar._type == TSCALAR ) return scalar.isHigh() ? this : scalar;
        return BOT;
    }

    @Override Type xmeet(Type t) {
        TypeScalar scalar = (TypeScalar)t;
        return isHigh() ? scalar : this;
    }

    @Override TypeScalar xdual() { return new TypeScalar(); }
    @Override public boolean isHigh() { return this == TOP; }
    @Override boolean _isGLB(boolean mem) { return this == BOT; }
    @Override Type _glb(boolean mem) { return BOT; }

    @Override int TAGOFF() { return 1; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs) {
        assert this == BOT;
        baos.write(TAGOFFS[_type]);
    }
    static TypeScalar packed(int tag) {
        assert tag == 0;
        return BOT;
    }

    @Override public String str() { return isHigh() ? "~scalar" : "scalar"; }
    @Override SB _print(SB sb, BitSet visit, boolean html) { return sb.p(str()); }
}
