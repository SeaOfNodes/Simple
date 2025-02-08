package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
// Conditional branch such as: BEQ
public class CBranchRISC extends IfNode implements MachNode{
    final String _bop;
    // label is obtained implicitly
    CBranchRISC( IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }

    // TOdo: post Select
    @Override public String label() { return op(); }

    @Override public RegMask regmap(int i) { assert i==1; return riscv.RMASK; }
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

    // Encoding is appended into the byte array; size is returned

    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(src);
    }

    @Override public String op() { return "b"+_bop; }

    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
