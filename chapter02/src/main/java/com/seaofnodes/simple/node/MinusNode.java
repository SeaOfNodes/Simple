package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

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
    public Type compute() { return TypeBot.BOTTOM; }

    @Override
    public Node idealize() { return null; }
}
