package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.BAOS;
import java.util.*;

/**
 *  Return Program Control or Return PC or RPC
 */
public class TypeRPC extends Type {

    // A set of CallEndNode IDs (or StopNode); commonly just one.  High-bit
    // XInt sets represent complements/infinite sets.
    public int[] _rpcs;

    private static final Ary<TypeRPC> FREE = new Ary<>(TypeRPC.class);
    private TypeRPC init(int[] rpcs) { _rpcs = XInt.intern(rpcs); return this; }
    private TypeRPC() { super(TRPC); }
    private static TypeRPC malloc(int[] rpcs) {
        return (FREE.isEmpty() ? new TypeRPC() : FREE.pop()).init(rpcs);
    }
    private static TypeRPC make(int[] rpcs) {
        TypeRPC rpc = malloc(rpcs);
        TypeRPC t2 = rpc.intern();
        return t2==rpc ? rpc : t2.free(rpc);
    }
    @Override TypeRPC free(Type t) {
        TypeRPC rpc = (TypeRPC)t;
        rpc._rpcs = null;
        rpc._dual = null;
        rpc._hash = 0;
        FREE.push(rpc);
        return this;
    }
    @Override boolean isFree() { return _rpcs==null; }

    public static TypeRPC constant(int cend) {
        return make(XInt.make(cend));
    }

    public int rpc() {
        return XInt.onlyBit(_rpcs);
    }


    public  static final TypeRPC BOT = make(XInt.FULL);
    private static final TypeRPC TEST2 = constant(2);
    private static final TypeRPC TEST3 = constant(2);
    public  static final TypeRPC EMPTY = make(XInt.EMPTY);

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(TEST2); ts.add(TEST3); }

    @Override public TypeRPC makeZero() { return EMPTY; }


    @Override public String str() {
        if( _rpcs == XInt.FULL  ) return "$[ALL]";
        if( _rpcs == XInt.EMPTY ) return "$[]";
        return "$"+XInt.str(_rpcs);
    }

    @Override
    public TypeRPC xmeet(Type other) {
        TypeRPC rpc = (TypeRPC)other;
        return make(XInt.meet(_rpcs,rpc._rpcs));
    }

    @Override TypeRPC xdual() { return malloc(XInt.dual(_rpcs)); }
    @Override Type rdual() {
        TypeRPC d = xdual();
        (_dual = d)._dual = this;
        return d;
    }

    @Override boolean _isConstant() { return XInt.isConstant(_rpcs); }
    @Override boolean _isGLB(boolean mem) { return true; }

    // Reserve tags for ALL, singleton, generic
    @Override int TAGOFF() { return 3; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        if( this==BOT ) {
            baos.write( TAGOFFS[_type] + 0 );
        } else if( XInt.isConstant(_rpcs) ) { // Singleton
            baos.write(TAGOFFS[_type] + 1);
            baos.packed2(rpc());
        } else {                // Generic
            assert !XInt.isHigh(_rpcs); // Not serializing above-center RPC
            baos.write(TAGOFFS[_type] + 2);
            XInt.packed(baos,_rpcs);
        }
    }
    static TypeRPC packed( int tag, BAOS bais ) {
        if( tag==0 ) return BOT;
        if( tag==1 ) return malloc(XInt.make(bais.packed2()));
        return malloc(XInt.packed(bais));
    }

    @Override
    int hash() { return XInt.hash(_rpcs); }
    @Override
    public boolean eq( Type t ) {
        TypeRPC rpc = (TypeRPC)t; // Contract
        return _rpcs==rpc._rpcs;
    }

}
