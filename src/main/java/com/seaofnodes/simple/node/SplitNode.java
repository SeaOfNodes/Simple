package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;

public abstract class SplitNode extends MachConcreteNode {
    public final String _kind;  // Kind of split
    public final byte _round;
    public SplitNode(String kind, byte round, Node[] nodes) { super(nodes); _kind = kind; _round = round; }
    @Override public String op() { return "mov"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("mov(");
        if( in(1) == null ) p.p("---");
        else p.n(in(1));
        return p.close();
    }
    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() { return null; }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
    @Override public String comment() { return _kind + " #"+ _round; }
}
