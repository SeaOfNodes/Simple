package com.seaofnodes.simple.node;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public abstract class SplitNode extends MachConcreteNode {
    public final String _kind;  // Kind of split
    public final byte _round;
    public SplitNode(String kind, byte round, Node[] nodes) { super(nodes); _kind = kind; _round = round; }
    @Override public String op() { return "mov"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("mov(");
        if( in(1) == null ) sb.append("---");
        else in(1)._print0(sb,visited);
        return sb.append(")");
    }
    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() { return null; }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
    @Override public String comment() { return _kind + " #"+ _round; }
}
