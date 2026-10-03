package com.seaofnodes.simple.type;

import java.util.ArrayList;

/** A token for all memory; field values are not tracked in its type. */
public class TypeMem extends Type {
    private final boolean _high;
    private TypeMem(boolean high) { super(TMEM); _high = high; }
    public static final TypeMem TOP = new TypeMem(true ).intern();
    public static final TypeMem BOT = new TypeMem(false).intern();

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); }
    @Override TypeMem xmeet(Type t) { return BOT; }
    @Override public Type dual() { return _high ? BOT : TOP; }
    @Override public Type glb() { return BOT; }
    @Override int hash() { return _high ? 1 : 2; }
    @Override boolean eq(Type t) { return _high==((TypeMem)t)._high; }
    @Override public StringBuilder print(StringBuilder sb) { return sb.append(_high ? "MemTop" : "MemBot"); }
    @Override public String str() { return print(new StringBuilder()).toString(); }
}
