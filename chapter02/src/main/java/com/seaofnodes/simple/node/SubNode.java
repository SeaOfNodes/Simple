package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

public class SubNode extends Node {
    public SubNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String label() { return "Sub"; }

    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append("-"));
        return sb.append(")");
    }
  
    @Override
    public Type compute() { return TypeBot.BOTTOM; }

    @Override
    public Node idealize() { return null; }

}
