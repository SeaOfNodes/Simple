package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

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
        int dst=enc.reg(this);
        if( in(1) instanceof CmpFX86 ) {
            // MOV preserves comparison flags; unordered is true only for !=.
            X86.unary(enc,0xC7,0,dst);
            enc.add4(_bop.equals("!=") ? 1 : 0);
            enc.add1(0x7A).add1(dst>=4 ? 8 : 6); // JP past SETcc/MOVZX
        }
        X86.set(enc,x86_64_v2.jumpop(_bop,_unsigned)&15,dst);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(" = ").p(src);
    }
}
