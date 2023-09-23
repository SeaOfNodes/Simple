package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeInteger;

public class MinusNode extends Node {
    public MinusNode(Node in) {
        super(null, in);
    }

    @Override
    public String label() { return "Minus"; }
  
    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("(-"));
        return sb.append(")");
    }
  
    @Override
    public TypeInteger compute() {
        return TypeInteger.INT;
    }

    @Override
    public Node idealize() { return null; }
}
