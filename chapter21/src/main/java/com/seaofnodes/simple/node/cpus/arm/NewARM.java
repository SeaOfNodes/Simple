package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NewARM extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    @Override public String op() { return "alloc"; }
    @Override public RegMask    regmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return arm.armCallerSave(); }

    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        // bl
        enc.add4(arm.b(0b100101, 0));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }
}
