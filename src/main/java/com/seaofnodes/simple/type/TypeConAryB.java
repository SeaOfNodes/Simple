package com.seaofnodes.simple.type;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class TypeConAryB extends TypeConAry<byte[]> {
    private TypeConAryB(boolean any, byte widen, byte[] bs) { super(any,widen,bs); }
    @Override TypeConAryB _make( boolean any, byte widen ) { return new TypeConAryB(any,widen,_ary); }
    public static TypeConAryB make( byte[] bs ) { return new TypeConAryB(false,(byte)0,bs).intern(); }
    public static TypeConAryB make( String s ) { return make(s.getBytes()); }
    static final TypeConAryB ABC  = make("abc");
    static final TypeConAryB ABCD = make("abcd");
    @Override public long at8(int idx) { return _ary[idx]; }
    @Override public int len() { return _ary.length; }
    @Override public int log_size() { return 0; }
    @Override public String str() { return "[\""+new String(_ary)+"\"]"; }
    @Override boolean eq(Type t) { return t instanceof TypeConAryB ary && _widen==ary._widen && Arrays.equals(_ary,ary._ary); }
    @Override int hash() { return Arrays.hashCode(_ary); }
    @Override public void write( ByteArrayOutputStream baos ) { baos.write(_ary,0,_ary.length); }
}
