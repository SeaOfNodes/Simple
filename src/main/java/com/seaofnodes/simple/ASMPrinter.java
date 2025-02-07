package com.seaofnodes.simple;

import com.seaofnodes.print.AssemblyAdapter;
import com.seaofnodes.print.ASMPrinter.Data;
import com.seaofnodes.print.ASMPrinter.Pool;
import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.ArrayList;

/** Machine-specific facts; traversal, columns and byte display live in print/. */
public final class ASMPrinter extends AssemblyAdapter<Node> {
    private final CodeGen _code;
    private ASMPrinter(CodeGen code) {
        super(new IRPrinter());
        _code=code;
    }
    public static SB print(SB sb, CodeGen code) {
        return sb.p(new com.seaofnodes.print.ASMPrinter<>(new ASMPrinter(code)).print());
    }
    @Override public ArrayList<Node> blocks() {
        var blocks=new ArrayList<Node>();
        var cfg=_code._cfg;
        if( cfg!=null ) for( Node n : cfg ) blocks.add(n);
        return blocks;
    }
    @Override public String op(Node n) { return n instanceof MachNode m ? m.op() : n.label(); }
    @Override public String register(Node n) { return _code.reg(n); }
    @Override public String comment(Node n) { return n.comment(); }
    @Override public String phiName(Node n) { return ((PhiNode)n)._label; }
    @Override public String operands(Node n) {
        SB sb=new SB();
        if( n instanceof MachNode m ) m.asm(_code,sb);
        else if( !(n._type instanceof TypeMem) ) {
            sb.p(n._nid).p(": ");
            for( Node def : n._inputs ) sb.p(def==null ? "___" : Integer.toString(def._nid)).p(' ');
        }
        return sb.toString();
    }
    @Override public boolean labelBlock(Node n) {
        return !(n instanceof FunNode) && !(n instanceof IfNode) && !(n instanceof CallNode) &&
            !(n instanceof CallEndNode) && !(n instanceof CProjNode && ir.input0(n) instanceof CallEndNode);
    }
    @Override public boolean hidden(Node n) {
        if( n instanceof PhiNode || n instanceof ProjNode )
            if( n._type instanceof TypeMem || n._type instanceof TypeRPC ) return true;
        if( n instanceof MemMergeNode ) return true;
        return n.getClass()==ConstantNode.class;
    }
}
