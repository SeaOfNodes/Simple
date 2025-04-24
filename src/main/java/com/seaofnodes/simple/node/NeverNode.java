package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeTuple;

import java.util.BitSet;

// "Never true" for infinite loop exits
public class NeverNode extends IfNode {
    public NeverNode(NeverNode ctrl) { super(ctrl); }
    public NeverNode(Node ctrl) { super(ctrl,null); }

    @Override public String label() { return "Never"; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Never"); }

    @Override public Type compute() { return TypeTuple.IF_BOTH; }

    @Override public Node idealize() { return null; }
}
