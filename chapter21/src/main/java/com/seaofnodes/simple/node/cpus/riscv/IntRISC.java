package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeInteger;

// Integer constants
public class IntRISC extends ConstantNode implements MachNode {
    IntRISC(ConstantNode con) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return riscv.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public IntRISC copy() { return new IntRISC(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // lui Load Upper Imm U 0110111
        LRG frd_self = CodeGen.CODE._regAlloc.lrg(this);
        
        short rd_reg = frd_self.get_reg();
        int beforeSize = bytes.size();

        // long MIN_SIGNED_20 = -524288;
        // long MAX_SIGNED_20 = 524287;

        TypeInteger ti = (TypeInteger)_con;
        long imm32_64 = ti.value();

        // It’s not needed because we are assuming the worst-case scenario, so no optimal case applies.
        // Todo: if it fits into 12 bits just use addi
        // Todo: if its between [13, 20] and [20, 32] use mixture of addi and lui
        // boolean fitsInSigned20 = (imm32_64 >= MIN_SIGNED_20 && imm32_64 <= MAX_SIGNED_20);
        // check if immediate fit into 20 bits
        // if not create extra add

        // low 12 bit of the immediate goes into add immediate
        // addi t0, t0, low12bit
        // 0xFFF = 1111 1111 1111
        // check if it needs 64 bit encoding
        int low_12_bits = (int)(imm32_64 & 0xFFF);
        if((low_12_bits & 0x800) != 0) imm32_64 += 4096;

        int upper20_bits =  ((int)imm32_64 >> 12) & 0xFFFFF;
        int lui_body = riscv.u_type(0x37, rd_reg, upper20_bits);
        riscv.push_4_bytes(lui_body, bytes);

        // low 12 bit of the immediate goes into add immediate
        // addi t0, t0, low12bit
        // 0xFFF = 1111 1111 1111
        if(imm32_64 < Integer.MIN_VALUE || imm32_64 > Integer.MAX_VALUE) {
            // handle long case here
            // TODO: relocs
        }
        int addi_body = riscv.i_type(riscv.I_TYPE, rd_reg, 0, rd_reg, (int)(imm32_64 & 0xFFF));
        riscv.push_4_bytes(addi_body, bytes);

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
