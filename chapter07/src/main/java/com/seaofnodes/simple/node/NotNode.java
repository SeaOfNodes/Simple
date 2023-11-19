package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.Set;

public class NotNode extends Node {
    public NotNode(Node in) { super(null, in); }

    @Override public String label() { return "Not"; }
  
    @Override public String glabel() { return "!"; }
  
    @Override
    StringBuilder _print1(StringBuilder sb, Set<Integer> visited) {
        in(1)._print0(sb.append("(!"), visited);
        return sb.append(")");
    }
  
    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 )
            return i0.isConstant() ? TypeInteger.constant(i0.value()==0 ? 1 : 0) : i0;
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
