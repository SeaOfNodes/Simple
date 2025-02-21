package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Compare immediate.  Sets flags.
// Corresponds to the x86 instruction "sete && setne".
// Use result of comparison without jump.
public class SetX86 extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    // Constructor expects input is an X86 and not an Ideal node.
    SetX86( Node cmp, String bop ) {
        super(cmp);
        _inputs.setLen(1);   // Pop the cmp inputs
        // Replace with the matched cmp
        _inputs.push(cmp);
        _bop = bop;
    }

    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX + 0F 94
        LRG set_rg = CodeGen.CODE._regAlloc.lrg(this);
        short reg = set_rg.get_reg();

//        // Clear bits prior
//        x86_64_v2.clear_bits(reg, reg, bytes);

        bytes.write(x86_64_v2.rex(0, reg, 0));
        bytes.write(0x0F); // opcode

        bytes.write(x86_64_v2.setop(_bop));
        x86_64_v2.print_as_hex(bytes);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0, reg));
        x86_64_v2.print_as_hex(bytes);

        // low 8 bites are set, now zero extend for next instruction
        x86_64_v2.zero_extend(reg, reg, bytes);
        return bytes.size();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(" = ").p(src);
    }

    @Override public String op() { return "set"+_bop; }

}
