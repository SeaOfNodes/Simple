package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Integer constants
public class IntARM extends ConstantNode implements MachNode {
    IntARM( ConstantNode con ) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public IntARM copy() { return new IntARM(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // MOVK X0 = lower bits
        LRG frd_self = CodeGen.CODE._regAlloc.lrg(this);

        short rd_reg = frd_self.get_reg();
        int beforeSize = bytes.size();

        final long MIN_16BIT = -32768;
        final long MAX_16BIT = 32767;

        TypeInteger ti = (TypeInteger)_con;
        long imm32_64 = ti.value();

        if (imm32_64 >= MIN_16BIT && imm32_64 <= MAX_16BIT) {
            int body = arm.mov(485, 0, (int)imm32_64, rd_reg);
            arm.push_4_bytes(body, bytes);
        } else {
            throw Utils.TODO("Handle cases bigger than 16 bits");
        }

        return bytes.size() - beforeSize;
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" = #"));
    }

    @Override public String op() {
        return "ldi";           // Some fancier encoding
    }

}
