package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

// Compare immediate.  Sets flags.
public class CmpIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    final String _bop;
    CmpIX86( BoolNode bool, TypeInteger ti ) {
        super(bool);
        _inputs.pop(); // Toss ideal ConstantNode away, embedded into machine op
        _bop = bool.op();       // One of <,<=,==
        _imm = (int)ti.value();
        assert _imm == ti.value();
    }
    CmpIX86( Node cmp, double ignore ) {
        super(cmp);
        _bop = "==";
        _imm = 0;
    }

    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + 81 /7 id	CMP r/m64, imm32
        // REX.W + 83 /7 ib	CMP r/m64, imm8
        LRG rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        short reg1 = rg_1.get_reg();
        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(0, reg1, 0));

        // switch between int and short
        int imm32_8 = _imm;

        // opcode
        int imm_size = x86_64_v2.imm_size(imm32_8);
        if(imm_size == 32) bytes.write(0x81);
        else if(imm_size == 8) bytes.write(0x83);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x07, reg1));

        x86_64_v2.imm(_imm, imm_size, bytes);
        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="FLAGS" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }

    @Override public String op() { return _imm==0 ? "test" : "cmp"; }

}
