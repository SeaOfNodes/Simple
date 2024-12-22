package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Nil-able Scalar types
 */
public abstract class TypeNil extends Type {
    // 0 = high-subclass choice nil
    // 1 = high-subclass no nil
    // 2 = low -subclass no nil
    // 3 = low -subclass also nil
    public final byte _nil;

    TypeNil(byte t, byte nil ) { super(t); _nil = nil; }

    public static void gather(ArrayList<Type> ts) { }

    abstract TypeNil makeFrom(byte nil);

    byte xmeet0(TypeNil that) { return (byte)Math.max(_nil,that._nil); }

    byte dual0() { return (byte)(3-_nil); }

    // RHS is NIL
    abstract Type meet0();

    // RHS is XNIL
    Type meetX() {
        return _nil==0 ? XNIL : (_nil<=2 ? TypePtr.NPTR : TypePtr.PTR);
    }

    Type nmeet(TypeNil tn) {
        // Invariants: both are TypeNil subclasses and unequal classes.
        // If this is TypePtr, we went to TypePtr.nmeet and not here.
        // If that is TypePtr, this is not (invariant); reverse and go again.
        if( tn instanceof TypePtr ts ) return ts.nmeet(this);

        // Two mismatched TypeNil, no Scalar.
        if( _nil==0 && tn._nil==0 ) return XNIL;
        if( _nil<=2 && tn._nil<=2 ) return TypePtr.NPTR;
        return TypePtr.PTR;
    }

    @Override public boolean isHigh       () { return _nil <= 1; }
    @Override public boolean isConstant   () { return false; }
    @Override public boolean isHighOrConst() { return isHigh() || isConstant(); }

    final String q() { return _nil == 1 || _nil == 2 ? "" : "?"; }
    final String x() { return isHigh() ? "~" : ""; }

    int hash() { return _nil<<17; }

    boolean eq(TypeNil ptr) { return _nil == ptr._nil; }
}
