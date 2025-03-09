package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;

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

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("if( "),visited).append(_bop);
        if( in(2)==null ) sb.append("0");
        else in(2)._print0(sb,visited);
        return sb.append(" )");
    }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // Todo: relocs (for offset - immf)
//        LRG branch_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG branch_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG branch_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        // no rd
        short reg1 = branch_rg_1.get_reg();
        short reg2 = branch_rg_2.get_reg();

        int beforeSize = bytes.size();
        int body = riscv.b_type(0x63, 0, riscv.jumpop(_bop), reg1, reg2, 0);
        riscv.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(in(2)==null ? "#0" : code.reg(in(2)));
    }

    @Override public String op() { return "b"+_bop; }

    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
