package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// unconditional jump
public class UJmpRISC extends CFGNode implements MachNode, RIPRelSize {
    @Override public String op() { return "jmp"; }
    @Override public String label() { return op(); }
    @Override public StringBuilder _print1( StringBuilder sb, BitSet visited ) { return sb.append("jmp "); }
    @Override public RegMask regmap(int i) {return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }
    @Override public void encoding( Encoding enc ) {
        // Short form +/-4K:  beq r0,r0,imm12
        // Long form:  auipc rX,imm20/32; jal r0,[rX+imm12/32]
        enc.jump(this,uctrl());
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
        CFGNode target = uctrl();
        //assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
