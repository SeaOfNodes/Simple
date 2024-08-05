package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;

public class XCtrlNode extends CFGNode {
    public XCtrlNode( ) { super(Parser.START); }
    @Override public String label() { return "Xctrl"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("Xctrl"); }
    @Override  public Type compute() { return Type.XCONTROL; }
    @Override public Node idealize() { return null; }
}
