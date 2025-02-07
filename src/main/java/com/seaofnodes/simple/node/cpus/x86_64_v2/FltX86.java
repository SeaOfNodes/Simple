package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;

import java.io.ByteArrayOutputStream;

public class FltX86 extends ConstantNode implements MachNode {
    FltX86(ConstantNode con ) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return RegMask.EMPTY; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
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
