package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;

// Conditional branch such as: BEQ
public class BranchRISC extends IfNode implements MachNode {
    String _bop;
    // label is obtained implicitly
    BranchRISC( IfNode iff, String bop, Node n1, Node n2 ) {
        super(iff);
        _bop = bop;
        _inputs.setX(1,n1);
        _inputs.setX(2,n2);
    }

    @Override public String op() { return "b"+_bop; }
    @Override public String label() { return op(); }
    @Override public String comment() { return "L"+cproj(1)._nid; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void invert() {
        if( _bop.equals("<") || _bop.equals("<=") )
            swap12();           // Cannot invert the test, so swap the operands
        else
            _bop = invert(_bop);
    }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("if( "),visited).append(_bop);
        if( in(2)==null ) sb.append("0");
        else in(2)._print0(sb,visited);
        return sb.append(" )");
    }

    @Override public void encoding( Encoding enc ) {
        enc.jump(this,cproj(0));
        // Todo: relocs (for offset - immf)
        short src1 = enc.reg(in(1));
        short src2 = in(2)==null ? (short)riscv.ZERO : enc.reg(in(2));
        int body = riscv.b_type(0x63, 0, riscv.jumpop(_bop), src1, src2, 0);
        enc.add4(body);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(in(2)==null ? "#0" : code.reg(in(2))).p(" ");
        CFGNode prj = cproj(0);
        while( prj.nOuts() == 1 )
            prj = prj.uctrl();       // Skip empty blocks
        sb.p(label(prj));
    }
}
