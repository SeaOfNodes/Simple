package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

// Integer constants
public class IntX86 extends ConstantNode implements MachNode {
    IntX86( ConstantNode con ) { super(con); }
    @Override public String op() {
        return _con == Type.NIL || _con == TypeInteger.ZERO ? "xor" : "ldi";
    }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new IntX86(this); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Zero-set uses XOR kills flags
    @Override public RegMask killmap() {
        return _con == Type.NIL || _con == TypeInteger.ZERO ? x86_64_v2.FLAGS_MASK : null;
    }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this);
        // Short form for zero
        if( _con==Type.NIL || _con==TypeInteger.ZERO ) {
            // XOR dst,dst.  Can skip REX is dst is low 8, makes this a 32b
            // xor, which will also zero the high bits.
            if( dst >= 8 ) enc.add1(x86_64_v2.rex(dst, dst, 0));
            enc.add1(0x33); // opcode
            enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, dst));
            return;
        }

        // Simply move the constant into a GPR
        // Conditional encoding based on 64 or 32 bits
        //REX.W + C7 /0 id	MOV r/m64, imm32

        long imm = ((TypeInteger)_con).value();
        if (Integer.MIN_VALUE <= imm && imm < 0) {
            // We need sign extension, so use imm32 into 64 bit register
            // REX.W + C7 /0 id    MOV r/m64, imm32
            enc.add1(x86_64_v2.rex(0, dst, 0));
            enc.add1(0xC7); // 32 bits encoding
            enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x00, dst));
            enc.add4((int)imm);
        } else if (0 <= imm && imm <= 0xFFFFFFFFL) {
            // We want zero extension into 64 bit register
            // so move 32 bit into 32 bit register which zeros
            // the upper bits in the 64 bit register.
            // B8+ rd id     MOV r32, imm32
            if (dst >= 8) enc.add1(0x41);
            enc.add1(0xB8 + (dst & 0x07));
            enc.add4((int)imm);
        } else {
            // Just write the full 64 bit constant
            // REX.W + B8+ rd io     MOV r64, imm64
            enc.add1(x86_64_v2.rex(0, dst, 0));
            enc.add1(0xB8 + (dst & 0x07));
            enc.add8(imm);               // 64 bits encoding
        }
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
}
