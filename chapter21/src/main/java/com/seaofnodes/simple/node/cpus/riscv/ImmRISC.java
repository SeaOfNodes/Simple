package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

abstract public class ImmRISC extends MachConcreteNode implements MachNode {
    public final int _imm12;
    ImmRISC( Node add, int imm12, boolean pop ) {
        super(add);
        _imm12 = imm12;
        // If pop, we are moving from an ideal Node to MachNode; the ideal Node
        // inputs got copied here, but we are absorbing the immediate into the
        // MachNode and so do not want the constant input.
        if( pop ) _inputs.pop();
        // if NOT pop, we are the 2nd-half of a 2-part expansion, reset the
        // input edges to no-control
        else { _inputs.set(0,null); _inputs.setX(1,add); }
    }
    ImmRISC( Node add, int imm12 ) { this(add,imm12,true); }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() {
        // Allow cloning of the 2nd half of a 2-part constant; this might
        // trigger cloning the first part also.
        return in(1) instanceof LUI || in(1) instanceof AUIPC;
    }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        int imm12 = (_imm12<<20)>>20; // Sign extend 12 bits
        in(1)._print0(sb.append("( "), visited);
        return sb.append(String.format(" %s #%d )",glabel(),imm12));
    }

    abstract int opcode();
    abstract int func3();

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        int body = riscv.i_type(opcode(), dst, func3(), src, _imm12 & 0xFFF);
        enc.add4(body);
    }

    // General form: "addi  rd = rs1 + imm"
    @Override public void asm(CodeGen code, SB sb) {
        int imm12 = (_imm12<<20)>>20; // Sign extend 12 bits
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ").p(glabel()).p(" #").p(imm12);
        if( in(1) instanceof LUI lui )
            sb.p(" // #").hex4((int)(((TypeInteger)lui._con).value()) + imm12);
    }
}
