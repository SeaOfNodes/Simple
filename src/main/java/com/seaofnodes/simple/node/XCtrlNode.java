package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public class XCtrlNode extends CFGNode {
    public XCtrlNode() { super(new Node[]{CodeGen.CODE._start}); }
    @Override public String label() { return "Xctrl"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Xctrl"); }
    @Override public boolean isConst() { return true; }
    @Override  public Type compute() { return Type.XCONTROL; }
    @Override public Node idealize() { return null; }
}
