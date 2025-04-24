package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public class CtrlNode extends CFGNode {
    public CtrlNode() { super(CodeGen.CODE._start); }
    @Override public String label() { return "Ctrl"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Cctrl"); }
    @Override public boolean isConst() { return true; }
    @Override  public Type compute() { return Type.CONTROL; }
    @Override public Node idealize() { return null; }
}
