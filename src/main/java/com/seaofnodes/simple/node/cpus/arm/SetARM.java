package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// conditional set?
// e.g CSET, stores flag info into GPRS after compare
public class SetARM extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    SetARM(Node cmp, String bop) {
        super(cmp);
        _inputs.setLen(1);   // Pop the cmp inputs
        // Replace with the matched cmp
        _inputs.push(cmp);
        _bop = bop;
    }
    @Override public String op() { return "set"+_bop; }
    @Override public RegMask regmap(int i) { assert i==1; return arm.FLAGS_MASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public void encoding( Encoding enc ) {
        int body = arm.cset(arm.OP_CSET,0b11111, arm.make_condition(_bop), 0b011111, enc.reg(this));
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(" = ").p(src);
    }
}
