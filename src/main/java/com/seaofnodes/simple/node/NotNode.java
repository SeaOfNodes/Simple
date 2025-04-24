package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

public class NotNode extends Node {
    public NotNode(Node in) { super(null, in); }

    @Override public String label() { return "Not"; }

    @Override public String glabel() { return "!"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("(!"), visited);
        return sb.append(")");
    }

    @Override
    public TypeInteger compute() {
        Type t0 = in(1)._type;
        if( t0.isHigh() )  return TypeInteger.BOOL.dual();
        if( t0 == Type.NIL || t0 == TypeInteger.ZERO ) return TypeInteger.TRUE;
        if( t0 instanceof TypeNil tn && tn.notNull() ) return TypeInteger.FALSE;
        if( t0 instanceof TypeInteger i && (i._min > 0 || i._max < 0) ) return TypeInteger.FALSE;
        return TypeInteger.BOOL;
    }

    @Override
    public Node idealize() { return null; }
}
