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

    private TypeMem(int alias) { super(TMEM); _alias = alias; }

    public static TypeMem make(int alias) { return new TypeMem(alias).intern(); }
    public static final TypeMem TOP = make( 0);
    public static final TypeMem BOT = make(-1);

    public static void gather(ArrayList<Type> ts) { ts.add(make(1)); ts.add(BOT); }

    @Override
    TypeMem xmeet(Type t) {
        TypeMem that = (TypeMem) t; // Invariant: TypeMem and unequal
        return _alias==0 ? that : (that._alias==0 ? this : BOT);
    }

    @Override
    public Type dual() {
        if( _alias== 0 ) return BOT;
        if( _alias==-1 ) return TOP;
        return this;
    }

    @Override
    public Type glb() { return make(_alias); }

    @Override
    int hash() { return 9876543 + _alias; }

    @Override
    boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias;
    }

    @Override
    public StringBuilder print(StringBuilder sb) {
        return sb.append("MEM#").append( switch(_alias) {
            case  0 -> "TOP";
            case -1 -> "BOT";
            default -> ""+_alias;
            });
    }

    @Override public String str() { return toString(); }
}
