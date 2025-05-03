package com.seaofnodes.simple.type;

import java.util.ArrayList;
import java.util.Arrays;

public class TypeConAryB extends TypeConAry<byte[]> {
    private TypeConAryB(byte[] bs) { super(false,bs); }
    public static TypeConAryB make( byte[] bs ) { return new TypeConAryB(bs).intern(); }
    public static TypeConAryB make( String s ) { return make(s.getBytes()); }
    static final TypeConAryB ABC  = make("abc");
    static final TypeConAryB ABCD = make("abcd");
    public static void gather(ArrayList<Type> ts) { ts.add(ABC); ts.add(ABCD); }
    @Override long at(int idx) { return _ary[idx]; }
    @Override int len() { return _ary.length; }
    @Override public int alignment() { return 0; }
    @Override public String str() { return "[\""+new String(_ary)+"\"]"; }
    @Override boolean eq(Type t) { return Arrays.equals(_ary,((TypeConAryB)t)._ary); }
    @Override int hash() { return Arrays.hashCode(_ary); }
}
