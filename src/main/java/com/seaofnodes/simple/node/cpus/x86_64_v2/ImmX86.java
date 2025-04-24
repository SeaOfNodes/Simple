package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

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

    abstract int opcode();
    abstract int mod();

    @Override public final void encoding( Encoding enc ) {
        short dst = enc.reg(this); // Also src1
        enc.add1(x86_64_v2.rex(0, dst, 0));
        // opcode; 0x81 or 0x83; 0x69 or 0x6B
        enc.add1( opcode() + (x86_64_v2.imm8(_imm) ? 2 : 0) );
        enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, mod(), dst) );
        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        if( x86_64_v2.imm8(_imm) ) enc.add1(_imm);
        else                       enc.add4(_imm);
    }

    // General form: "addi  dst += #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= #").p(_imm);
    }
}
