package com.seaofnodes.simple.type;

import java.util.ArrayList;
import java.util.Arrays;

public class TypeConAryI extends TypeConAry<int[]> {
    private TypeConAryI(int[] bs) { super(false,bs); }
    public static TypeConAryI make( int[] bs ) { return new TypeConAryI(bs).intern(); }
    private static final TypeConAryI I123  = make(new int[]{1,2,3});
    public static void gather(ArrayList<Type> ts) { ts.add(I123); }
    @Override public String str() { return "[i32]"; }
    @Override public int alignment() { return 2; }
    @Override boolean eq(Type t) { return Arrays.equals(_ary,((TypeConAryI)t)._ary); }
    @Override int hash() { return Arrays.hashCode(_ary); }
}
