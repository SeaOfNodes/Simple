package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;

public class CtrlNode extends CFGNode {
    public CtrlNode() { super(CodeGen.CODE._start); }
    @Override public String label() { return "Ctrl"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("Cctrl"); }
    @Override public boolean isConst() { return true; }
    @Override  public Type compute() { return Type.CONTROL; }
    @Override public Node idealize() { return null; }
}
