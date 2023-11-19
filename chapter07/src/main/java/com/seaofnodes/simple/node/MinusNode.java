package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.Set;

public class MinusNode extends Node {
    public MinusNode(Node in) { super(null, in); }

    @Override public String label() { return "Minus"; }
  
    @Override public String glabel() { return "-"; }
  
    @Override
    StringBuilder _print1(StringBuilder sb, Set<Integer> visited) {
        in(1)._print0(sb.append("(-"), visited);
        return sb.append(")");
    }
  
    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0)
            return i0.isConstant() ? TypeInteger.constant(-i0.value()) : i0;
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
