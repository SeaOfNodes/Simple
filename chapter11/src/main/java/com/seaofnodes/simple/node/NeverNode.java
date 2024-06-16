package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// "Never true" for infinite loop exits
public class NeverNode extends IfNode {
    public NeverNode(Node ctrl) { super(ctrl,null); }

    @Override public String label() { return "Never"; }

    @Override StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Never"); }

    @Override public Type compute() { return TypeInteger.BOT; }

    @Override public Node idealize() { return null; }
}
