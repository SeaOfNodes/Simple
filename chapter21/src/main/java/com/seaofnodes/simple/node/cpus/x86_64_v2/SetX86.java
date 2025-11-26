package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Corresponds to the x86 instruction "sete && setne".
// Use result of comparison without jump.
public class SetX86 extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    final boolean _unsigned;
    // Constructor expects input is an X86 and not an Ideal node.
    SetX86( Node cmp, String bop, boolean unsigned ) {
        super(cmp);
        _inputs.setLen(1);   // Pop the cmp inputs
        // Replace with the matched cmp
        _inputs.push(cmp);
        _bop = bop;
        _unsigned = unsigned;
    }
    @Override public String op() { return "set"+_bop; }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    @Override public void encoding( Encoding enc ) {
        // REX + 0F 94
        short dst = enc.reg(this );

        // Optional rex, for dst
        if( dst >= 8 ) enc.add1(x86_64_v2.rex(0, dst, 0));
        enc.add1(0x0F);         // opcode
        enc.add1(switch (_bop) {
            case "==" -> 0x94;  // SETE
            case "<"  -> _unsigned ? 0x92 : 0x9C;  // SETB /SETL
            case "<=" -> _unsigned ? 0x96 : 0x9E;  // SETBE/SETLE
            case ">=" -> _unsigned ? 0x93 : 0x9D;  // SETAE/SETGE
            default -> throw Utils.TODO();
            });
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0, dst));

        // low 8 bites are set, now zero extend for next instruction
        if( dst >= 8 ) enc.add1(x86_64_v2.rex(0, dst, 0));
        enc.add1(0x0F); // opcode
        enc.add1(0xB6); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, dst));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(" = ").p(src);
    }
}
