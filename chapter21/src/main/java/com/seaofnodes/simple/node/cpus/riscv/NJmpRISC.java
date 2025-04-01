package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.util.BitSet;

public class NJmpRISC extends NeverNode implements MachNode, RIPRelSize {
    // label is obtained implicitly
    public NJmpRISC( CFGNode cfg ) { super(cfg); }

    @Override public String op() { return "jmp"; }
    @Override public String label() { return op(); }
    @Override public String comment() { return "L"+cproj(1)._nid; }
    @Override public StringBuilder _print1( StringBuilder sb, BitSet visited ) { return sb.append("jmp "); }
    @Override public RegMask regmap(int i) {
        if( i!=3 ) return null;
        TypeFunPtr tfp = cproj(0).uctrl().fun().sig();
        return riscv.retMask(tfp,2);
    }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        // Short form +/-4K:  beq r0,r0,imm12
        // Long form:  auipc rX,imm20/32; jal r0,[rX+imm12/32]
        enc.jump(this,cproj(1));
        enc.add4(riscv.j_type(riscv.OP_JAL, 0, 0));
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( -(1L<<20) <= delta && delta < (1L<<20) ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        if( opLen==4 ) {
            enc.patch4(opStart,riscv.j_type(riscv.OP_JAL, 0, delta));
        } else {
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(label(cproj(1).uctrl()));
    }
}
