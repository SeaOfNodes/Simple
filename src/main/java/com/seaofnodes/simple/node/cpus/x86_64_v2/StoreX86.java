package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;

public class StoreX86 extends MemOpX86 {
    StoreX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st,st, base, idx, off, scale, imm, val);
    }

    @Override public String op() { return "st"+_sz; }

    @Override protected ExprPrinter<Node> _printMach(ExprPrinter<Node> p) {
        Node val = val();
        p.p(".").p(_name).p("=");
        if( val==null ) p.p(_imm);
        else p.n(val);
        return p.p(";");
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
