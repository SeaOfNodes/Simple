package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddRISC extends MachConcreteNode implements MachNode {
    AddRISC( Node add) {super(add); }
    AddRISC (Node in1, Node in2) { super(new Node[]{null, in1, in2}); }
    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // just R-TYPE encoding
        // add     a0,a1,a2
        // 3 operand instruction
        short self = enc.reg(this );
        short reg1 = enc.reg(in(1));
        short reg2 = enc.reg(in(2));
        // opcode
        int body = riscv.r_type(riscv.R_TYPE, self, 0, reg1, reg2, 0);
        enc.add4(body);
    }

    // General form: "rd = rs1 + rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }

    @Override public String op() { return "add"; }
}
