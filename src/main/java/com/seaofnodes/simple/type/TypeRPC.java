package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;

/**
 *  Return Program Control or Return PC or RPC
 */
public class TypeRPC extends Type {

    // A set of CallEndNode IDs (or StopNode); commonly just one.
    // Basically a sparse bit set
    HashSet<Integer> _rpcs;

    // If true, invert the meaning of the bits
    boolean _any;

    private static final Ary<TypeRPC> FREE = new Ary<>(TypeRPC.class);
    private TypeRPC init(boolean any, HashSet<Integer> rpcs) { _any=any; _rpcs=rpcs; return this; }
    private TypeRPC() { super(TRPC); }
    private static TypeRPC malloc(boolean any, HashSet<Integer> rpcs) {
        return (FREE.isEmpty() ? new TypeRPC() : FREE.pop()).init(any,rpcs);
    }
    private static TypeRPC make(boolean any, HashSet<Integer> rpcs) {
        TypeRPC rpc = (FREE.isEmpty() ? new TypeRPC() : FREE.pop()).init(any,rpcs);
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
        HashSet<Integer> rpcs = new HashSet<>();
        rpcs.add(cend);
        return make(false,rpcs);
    }

    public  static final TypeRPC BOT = make(true,new HashSet<>());
    private static final TypeRPC TEST2 = constant(2);
    private static final TypeRPC TEST3 = constant(2);

    public static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(TEST2); ts.add(TEST3); }

    @Override public String str() {
        if( _rpcs.isEmpty() )
            return _any ? "$[ALL]" : "$[]";
        if( _rpcs.size()==1 )
            for( Integer rpc : _rpcs )
                return _any ? "$[-"+rpc+"]" : "$["+rpc+"]";
        return "$["+(_any ? "-" : "")+_rpcs+"]";
    }

    @Override
    public TypeRPC xmeet(Type other) {
        TypeRPC rpc = (TypeRPC)other;
        // If the two sets are equal, the _any must be unequal (invariant),
        // so they cancel and all bits are set.
        if( _rpcs.equals(rpc._rpcs) )
            return BOT;
        // Classic union of bit sets (which might be infinite).
        HashSet<Integer> lhs = _rpcs, rhs = rpc._rpcs;
        // Smaller on left
        if( lhs.size() > rhs.size() ) { lhs = rpc._rpcs; rhs = _rpcs; }

        HashSet<Integer> rpcs = new HashSet<>();
        boolean any = true;
        // If both sets are infinite, intersect.
        if( _any && rpc._any ) {
            for( Integer i : lhs )  if( rhs.contains(i) )  rpcs.add(i);

        } else if( !_any && !rpc._any ) {
            // if neither set is infinite, union.
            rpcs.addAll( lhs );
            rpcs.addAll( rhs );
            any = false;
        } else {
            // If one is infinite, subtract the other from it.
            HashSet<Integer> sub = _any ? rpc._rpcs : _rpcs;
            HashSet<Integer> inf = _any ? _rpcs : rpc._rpcs;
            for( Integer i : inf )
                if( inf.contains(i) && !sub.contains(i) )
                    rpcs.add(i);
        }
        return make(any,rpcs);
    }

    @Override Type xdual() { return malloc(!_any,_rpcs); }

    @Override boolean _isConstant() { return !_any && _rpcs.size()==1; }
    @Override boolean _isGLB(boolean mem) { return true; }

    @Override
    int hash() { return _rpcs.hashCode() ^ (_any ? -1 : 0) ; }
    @Override
    public boolean eq( Type t ) {
        TypeRPC rpc = (TypeRPC)t; // Contract
        return _any==rpc._any && _rpcs.equals(rpc._rpcs);
    }

}
