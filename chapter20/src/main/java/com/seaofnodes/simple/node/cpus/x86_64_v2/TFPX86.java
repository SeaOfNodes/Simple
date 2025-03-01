package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

import java.io.ByteArrayOutputStream;

// Function constants
public class TFPX86 extends ConstantNode implements MachNode {
    TFPX86( ConstantNode con ) {  super(con); }
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        int beforeSize = bytes.size();
        // REX.W + 8D /r	LEA r64,m
        // load function pointer into a reg
        // opcode
        LRG tfp_lrg = CodeGen.CODE._regAlloc.lrg(this);

        short tfp_reg = tfp_lrg.get_reg();
        bytes.write(x86_64_v2.rex(0, 0, 0));
        bytes.write(0x8D);

        // hard-code rip here
        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, tfp_reg, 0x05));
        x86_64_v2.imm(0, 32, bytes);

        return bytes.size() - beforeSize;
    }

    @Override public boolean isClone() { return true; }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        if( _con == Type.NIL )
            sb.p(reg).p(",").p(reg);
        else
            _con.print(sb.p(reg).p(" #"));
    }

    @Override public String op() {
        if( _con == Type.NIL )
            return "xor";
        return "ldx";           // Some fancier encoding
    }
}
