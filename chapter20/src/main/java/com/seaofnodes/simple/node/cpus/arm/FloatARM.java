package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeInteger;

import java.io.ByteArrayOutputStream;

//FMOV (scalar, immediate)
//Floating-point move immediate.
public class FloatARM extends ConstantNode implements MachNode {
    FloatARM(ConstantNode con) { super(con); }
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }

    // General int registers
    @Override public RegMask outregmap() { return arm.DMASK; }

    @Override public boolean isClone() { return true; }
    @Override public FloatARM copy() { return new FloatARM(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // ENCODING for the 64-bit to double-precision variant
        int beforeSize = bytes.size();
        LRG frd_self = CodeGen.CODE._regAlloc.lrg(this);

        short rd_reg = frd_self.get_reg();
        TypeInteger ti = (TypeInteger)_con;
        long imm32_64 = ti.value();

        int body = arm.f_mov(30, 1, (int)imm32_64, rd_reg);
        arm.push_4_bytes(body, bytes);
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
        return "fld";           // Some fancier encoding
    }
}
