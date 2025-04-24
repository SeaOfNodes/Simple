package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Compare immediate.  Sets flags.
public class CmpIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    CmpIX86( Node cmp, int imm, boolean swap ) {
        super(cmp);
        _inputs.del(swap ? 1 : 2);
        _imm = imm;
    }
    CmpIX86( CmpIX86 cmp ) {
        super((Node)null);
        addDef(null);
        addDef(cmp.in(1));
        _imm = cmp._imm;
    }

    @Override public String op() { return _imm==0 ? "test" : "cmp"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new CmpIX86(this); }

    @Override public final void encoding( Encoding enc ) {
        short dst = enc.reg(in(1)); // Also src1
        enc.add1(x86_64_v2.rex(0, dst, 0));
        // opcode; 0x81 or 0x83; 0x69 or 0x6B
        enc.add1( 0x81 + (x86_64_v2.imm8(_imm) ? 2 : 0) );
        enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 7, dst) );
        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        if( x86_64_v2.imm8(_imm) ) enc.add1(_imm);
        else                       enc.add4(_imm);
    }
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }
}
