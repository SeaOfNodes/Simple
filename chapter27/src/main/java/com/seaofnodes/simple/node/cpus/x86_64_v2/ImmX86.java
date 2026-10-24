package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;

public abstract class ImmX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    ImmX86( Node add, int imm ) {
        super(add);
        _inputs.pop();          // Pop constant input off
        _imm = imm;
    }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 1; }
    @Override public RegMask killmap() { return x86_64_v2.FLAGS_MASK; }

    abstract int opcode();
    abstract int mod();

    @Override public void encoding( Encoding enc ) {
        X86.imm(enc,opcode(),mod(),enc.reg(this),_imm);
    }

    // General form: "addi  dst += #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= #").p(_imm);
    }
}
