package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;

/**
 * Represents a constant array of primitives
 */
public class TypeConAry<A> extends Type {
    boolean _any;
    // One of byte,short,int,long,float,double array
    public final A _ary;

    TypeConAry( boolean any, A ary ) { super(TCONARY); _any = any; _ary = ary; }
    private static final TypeConAry TOP = new TypeConAry(true ,null).intern();
    public  static final TypeConAry BOT = new TypeConAry(false,null).intern();
    public static void gather(ArrayList<Type> ts) {
        ts.add(BOT);
        TypeConAryB.gather(ts);
        TypeConAryI.gather(ts);
    }

    @Override public String str() { return (_any?"~":"") + "[]"; }
    @Override public boolean isConstant() { return true; }
    @Override public TypeConAry dual() {
        if( this== BOT ) return TOP;
        if( this== TOP ) return BOT;
        return this;
    }

    @Override Type xmeet(Type t) {
        TypeConAry ary = (TypeConAry)t; // Invariant
        if( this==TOP ) return ary ;
        if( ary ==TOP ) return this;
        assert _ary!=ary._ary;  // Already interned and this!=t
        return BOT;
    }

    // Meet-over-elements type
    public Type elem() {
        if( _ary==null )
            return _any ? TOP : BOTTOM;
        int len = len();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for( int i=0; i<len; i++ ) {
            min = Math.min(min,at(i));
            max = Math.max(max,at(i));
        }
        return TypeInteger.make(min,max);
    }
    long at(int idx) { throw Utils.TODO(); }
    public int len() { throw Utils.TODO(); }
    @Override public int alignment() { return 0; }
    public void write( ByteArrayOutputStream baos ) { throw Utils.TODO(); }

    @Override boolean eq(Type t) {
        TypeConAry ary = (TypeConAry)t; // Invariant
        return _any==ary._any && ary._ary==null;
    }

    @Override int hash() {
        assert _ary==null;
        return _any ? 1024 : 0;
    }

}
