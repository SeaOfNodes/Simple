package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;

import java.io.ByteArrayOutputStream;

public class FltRISC extends ConstantNode implements MachNode{
    FltRISC(ConstantNode con) {
        super(con);
    }
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return riscv.FMASK; }

    @Override public boolean isClone() { return true; }
    @Override public FltRISC copy() { return new FltRISC(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        //fld rd,rs1,offset

        LRG fpr_con = CodeGen.CODE._regAlloc.lrg(this);
        short fpr_reg = fpr_con.get_reg();
        int beforeSize = bytes.size();

        TypeFloat ti = (TypeFloat)_con;
        int body = riscv.i_type(0x07, fpr_reg - riscv.F_OFFSET, 0X03, fpr_reg - riscv.F_OFFSET, (int)ti.value());

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

    @Override public String op() {
        return "flw";           // Some fancier encoding
    }
}
