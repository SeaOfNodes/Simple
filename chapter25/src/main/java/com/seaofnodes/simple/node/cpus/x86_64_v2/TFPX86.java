package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

// Function constants
public class TFPX86 extends FunPtrNode implements MachNode, RIPRelSize {
    private byte _opLen;
    final String _ext;          // External name
    TFPX86( FunPtrNode fptr, String ext ) { super(fptr); _ext = ext; }
    @Override public String op() { return "ldx"; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new TFPX86(this,_ext); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this);

        // lea to load function pointer address
        // lea rax, [rip+disp32]
        if( _ext==null ) {
            //enc.relo(this);     // Internal patch
            // internal patch
            throw Utils.TODO();
        } else
            enc.external(this,_ext); // ELF-file external patch
        // 0 or 1 for REX depending on the dst.
        // Zero/sign extend should be fine, so not wide.
        _opLen = (byte)(6+ x86_64_v2.rexF(dst, 0, 0, true, enc));
        enc.add1(0x8D); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0b101));
        enc.add4(0);
    }

    @Override public byte encSize(int delta) { return _opLen; }

    // Delta is from opcode start, X86 expects it from opcode end
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart + opLen - 4, delta - opLen);
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        sb.p(code.reg(this));
        if( _ext == null ) _type.print(sb);
        else sb.p(_ext);
    }
    @Override public boolean eq(Node n) { return this==n; }
}
