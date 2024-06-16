package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public class XCtrlNode extends CFGNode {
    public XCtrlNode( ) { super(Parser.START); }
    @Override public String label() { return "~ctrl"; }
    @Override StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("~ctrl"); }
    @Override  public Type compute() { return Type.XCONTROL; }
    @Override public Node idealize() { return null; }
}
