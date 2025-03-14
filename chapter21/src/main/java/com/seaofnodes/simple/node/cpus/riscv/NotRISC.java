package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.NotNode;

public class NotRISC extends ImmRISC {
    NotRISC(NotNode not) { super(not,1); }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.RMASK;  }
    @Override public String op() { return "not"; }
    // sltiu: x <u 1 ? 1 : 0;  only zero is unsigned-less-than 1
    @Override int opcode() { return 19; }
    @Override int func3() { return 2; }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
