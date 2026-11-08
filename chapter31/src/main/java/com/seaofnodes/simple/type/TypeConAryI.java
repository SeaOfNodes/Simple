package com.seaofnodes.simple.type;

import java.util.Arrays;

public class TypeConAryI extends TypeConAry<int[]> {
    private TypeConAryI(boolean any, byte widen, int[] bs) { super(any,widen,bs); }
    public static TypeConAryI make( int[] bs ) { return new TypeConAryI(false,(byte)0,bs).intern(); }
    @Override TypeConAryI _make( boolean any, byte widen ) { return new TypeConAryI(any,widen,_ary); }
    static final TypeConAryI I123 = make(new int[]{1,2,3});
    @Override public long at8(int idx) { return _ary[idx]; }
    @Override public int len() { return _ary.length; }
    @Override public int log_size() { return 2; }
    @Override public String str() { return "[i32]"; }
    @Override boolean eq(Type t) { return t instanceof TypeConAryI ary && _widen==ary._widen && Arrays.equals(_ary,ary._ary); }
    @Override int hash() { return Arrays.hashCode(_ary); }
}
