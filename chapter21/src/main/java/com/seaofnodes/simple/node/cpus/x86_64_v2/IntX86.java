package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

// Integer constants
public class IntX86 extends ConstantNode implements MachNode {

    IntX86( ConstantNode con ) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Zero-set uses XOR kills flags
    @Override public RegMask killmap() {
        return _con == Type.NIL || _con == TypeInteger.ZERO ? x86_64_v2.FLAGS_MASK : null;
    }

    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new IntX86(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Simply move the constant into a GPR
        // Conditional encoding based on 64 or 32 bits
        //REX.W + C7 /0 id	MOV r/m64, imm32
        LRG gpr_con = CodeGen.CODE._regAlloc.lrg(this);
        short gpr_reg = gpr_con.get_reg();

        boolean enc32; // assume 32 bits by default

        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(0, gpr_reg, 0));

        // immediate(4 bytes) 32 bits or 64 bits(8 bytes)
        TypeInteger ti = (TypeInteger)_con;
        long imm32_64 = ti.value();

        enc32 = imm32_64 >= Integer.MIN_VALUE && imm32_64 <= Integer.MAX_VALUE;

        if(enc32) bytes.write(0xC7); // 32 bits encoding
        else {
            bytes.write(0xB8 + (gpr_reg & 0x07));
        }  // 64 bits encoding

        if (enc32) {
            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x00, gpr_reg));
            x86_64_v2.imm((int)imm32_64, 32, bytes);
        } else {
            // just assume 64 bits for now, (folds into two 32 bit imm call)
            int lower32 = (int)(imm32_64 & 0xFFFFFFFFL);
            int upper32 = (int)((imm32_64 >> 32) & 0xFFFFFFFFL);

            x86_64_v2.imm(lower32, 32, bytes);

            x86_64_v2.imm(upper32, 32, bytes);
        }

        return bytes.size() - beforeSize;
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        if( _con == Type.NIL || _con == TypeInteger.ZERO )
            sb.p(reg).p(",").p(reg);
        else
            _con.print(sb.p(reg).p(" = #"));
    }

    @Override public String op() {
        if( _con == Type.NIL || _con == TypeInteger.ZERO )
            return "xor";
        return "ldi";           // Some fancier encoding
    }
}
