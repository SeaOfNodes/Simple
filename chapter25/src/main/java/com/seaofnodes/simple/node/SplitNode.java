package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.SB;

public abstract class SplitNode extends MachConcreteNode {
    public final LRG _lrg;      // Live range for split
    public final String _kind;  // Kind of split
    public final byte _round;   // Debugging info for which RegAlloc round
    public SplitNode(LRG lrg,String kind, byte round, Node[] nodes) { super(nodes); _lrg=lrg; _kind = kind; _round = round; }
    @Override public String op() { return "mov"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("mov(");
        if( in(1) == null ) p.p("---");
        else p.n(in(1));
        return p.close();
    }
    public FunNode fun(Encoding enc) { return enc._code._regAlloc.lrg(this)._fun; }
    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() { return null; }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
    @Override public String comment() { return _kind + " #"+ _round; }
}
