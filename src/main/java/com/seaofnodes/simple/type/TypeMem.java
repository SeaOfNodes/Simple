package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.lang.Long;
import java.util.BitSet;
import java.util.ArrayList;


/**
 * Represents a slice of memory corresponding to a set of aliases
 */
public class TypeMem extends Type {

    // Which slice of memory?
    //  0 means TOP, no slice.
    // -1 means BOT, all memory.
    //  N means slice#N.
    public final int _alias;
    public final Type _t;       // Memory contents, some scalar type

    private TypeMem(int alias, Type t) { super(TMEM); _alias = alias; _t = t; }

    public static TypeMem make(int alias, Type t) { return new TypeMem(alias,t).intern(); }
    public static final TypeMem TOP = make( 0, Type.TOP);
    public static final TypeMem BOT = make(-1, Type.BOTTOM);

    public static void gather(ArrayList<Type> ts) { ts.add(make(1,TypeInteger.ZERO)); ts.add(BOT); }

    @Override
    TypeMem xmeet(Type t) {
        TypeMem that = (TypeMem) t; // Invariant: TypeMem and unequal
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( this==BOT ) return BOT;
        if( that==BOT ) return BOT;
        int alias = _alias==that._alias ? _alias : -1;
        Type mt = _t.meet(that._t);
        return make(alias,mt);
    }

    @Override
    public Type dual() {
        if( _alias== 0 ) return BOT;
        if( _alias==-1 ) return TOP;
        return make(_alias,_t.dual());
    }

    @Override
    public Type glb() { return make(_alias,_t.glb()); }

    @Override
    int hash() { return 9876543 + _alias + _t.hashCode(); }

    @Override
    boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t;
    }

    @Override
    public StringBuilder print(StringBuilder sb) {
        sb.append("MEM#");
        if( _alias== 0 ) return sb.append("TOP");
        if( _alias==-1 ) return sb.append("BOT");
        return _t.print(sb.append(_alias).append(":"));
    }

    @Override public String str() { return toString(); }
}
