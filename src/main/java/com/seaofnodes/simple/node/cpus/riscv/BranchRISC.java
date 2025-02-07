package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

// Conditional branch such as: BEQ
public class BranchRISC extends IfNode implements MachNode {
    final String _bop;
    // label is obtained implicitly
    BranchRISC( IfNode iff, String bop, Node n1, Node n2 ) {
        super(iff);
        _bop = bop;
        _inputs.setX(1,n1);
        _inputs.setX(2,n2);
    }

    @Override public String label() { return op(); }

    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return null; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("if( ").n(in(1)).p(_bop);
        if( in(2)==null ) p.p("0");
        else p.n(in(2));
        return p.p(" )");
    }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(in(2)==null ? "#0" : code.reg(in(2)));
    }

    @Override public String op() { return "b"+_bop; }

    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
