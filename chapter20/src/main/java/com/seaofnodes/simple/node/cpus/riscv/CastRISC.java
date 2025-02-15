package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.lang.StringBuilder;


// fcvt.s.wu or fcvt.s.w
public class CastRISC extends MachConcreteNode implements MachNode {
    CastRISC(CastNode cast) {
        super(cast);
    }

    @Override public RegMask regmap(int i) { assert i==1; return riscv.WMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // General form
    // General form: "andi  rd = rs1 & imm"
    @Override public void asm(CodeGen code, SB sb) {
        throw Utils.TODO();
    }
    @Override public String op() { return "cast"; }
}
