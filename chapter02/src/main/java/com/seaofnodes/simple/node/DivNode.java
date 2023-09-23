package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeInteger;

public class DivNode extends Node {
    public DivNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String label() { return "Div"; }
  
    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append("/"));
        return sb.append(")");
    }
  
    @Override
    public TypeInteger compute() {
        return TypeInteger.INT;
    }

    @Override
    public Node idealize() { return null; }
}
