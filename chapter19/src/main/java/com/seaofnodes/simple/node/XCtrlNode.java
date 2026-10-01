package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;

public class XCtrlNode extends CFGNode {
    public XCtrlNode() { super(new Node[]{CodeGen.CODE._start}); }
    @Override public String label() { return "Xctrl"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("Xctrl"); }
    @Override public boolean isConst() { return true; }
    @Override public boolean isMultiTail() { return true; }
    @Override  public Type compute() { return Type.XCONTROL; }
    @Override public Node idealize() { return null; }
}
