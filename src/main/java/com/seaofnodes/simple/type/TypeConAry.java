package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Represents a constant array of primitives
 */
public class TypeConAry<A> extends Type {
    boolean _any;
    // One of byte,short,int,long,float,double array
    public final A _ary;

    TypeConAry( boolean any, byte type, A ary ) { super(type); _any = any; _ary = ary; }
    public static final TypeConAry BOT = new TypeConAry(false, TINT, null).intern();
    public static void gather(ArrayList<Type> ts) {
        ts.add(BOT);
        TypeConAryB.gather(ts);
        TypeConAryI.gather(ts);
    }

    @Override public String str() { return (_any?"~":"") + "[]"; }
    @Override TypeConAry xdual() {
        if( _ary==null )
            return new TypeConAry(!_any,_type,null);
        return this;
    }

    @Override Type xmeet(Type t) {
        if( t instanceof TypeInteger ti ) return imeet(ti);
        TypeConAry ary = (TypeConAry)t; // Invariant
        if( this==BOT ) return BOT;
        if( ary ==BOT ) return BOT;
        if( this==BOT.dual() ) return ary ;
        if( ary ==BOT.dual() ) return this;
        assert _ary!=ary._ary;  // Already interned and this!=t
        return elem().meet(ary.elem());
    }
    Type imeet( TypeInteger ti ) {
        if( this==BOT.dual() ) return ti;
        if( this==BOT        ) return BOT;
        Type elem = elem();
        if( !(elem instanceof TypeInteger) ) return BOTTOM;
        if( ti.isHigh() && elem.isa(ti.dual()) )
            return this;
        return elem.meet(ti);
    }


    @Override public boolean isHigh() { return this==TOP; }
    @Override boolean _isConstant() { return true; }
    @Override Type _glb(boolean mem) { return this; }
    @Override boolean _isGLB(boolean mem) { return true; }

    // Meet-over-elements type
    public Type elem() {
        if( _ary==null )
            return _any ? Type.TOP : BOTTOM;
        int len = len();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for( int i=0; i<len; i++ ) {
            min = Math.min(min,at8(i));
            max = Math.max(max,at8(i));
        }
        return TypeInteger.make(min,max);
    }
    public long at8(int idx) { throw Utils.TODO(); }
    public int len() { throw Utils.TODO(); }
    @Override public int log_size() { throw Utils.TODO(); }
    public void write( ByteArrayOutputStream baos ) { throw Utils.TODO(); }

    @Override boolean eq(Type t) {
        return t instanceof TypeConAry ary && _any==ary._any && ary._ary==null;
    }

    @Override int hash() {
        assert _ary==null;
        return _any ? 1024 : 0;
    }

}
