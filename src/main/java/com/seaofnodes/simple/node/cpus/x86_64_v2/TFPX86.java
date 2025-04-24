package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

// Function constants
public class TFPX86 extends ConstantNode implements MachNode, RIPRelSize {
    TFPX86( ConstantNode con ) {  super(con); }
    @Override public String op() {
        return _con == Type.NIL ? "xor" : "ldx";
    }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new TFPX86(this); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Zero-set uses XOR kills flags
    @Override public RegMask killmap() {
        return _con == Type.NIL ? x86_64_v2.FLAGS_MASK : null;
    }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this);
        // Short form for zero
        if( _con==Type.NIL ) {
            // XOR dst,dst.  Can skip REX is dst is low 8, makes this a 32b
            // xor, which will also zero the high bits.
            if( dst >= 8 ) enc.add1(x86_64_v2.rex(dst, dst, 0));
            enc.add1(0x33); // opcode
            enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, dst));
            return;
        }

        // lea to load function pointer address
        // lea rax, [rip+disp32]
        enc.relo(this);
        enc.add1(x86_64_v2.rex(dst, 0, 0));
        enc.add1(0x8D); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0b101));
        enc.add4(0);
    }

    @Override public byte encSize(int delta) {
        return 7;
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart + 3, delta - 7);
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        if( _con == Type.NIL )
            sb.p(reg).p(",").p(reg);
        else
            _con.print(sb.p(reg).p(" = #"));
    }
}
