package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.cpus.x86_64_v2.TFPX86;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

import java.io.ByteArrayOutputStream;

public class TFPRISC extends ConstantNode implements MachNode{
    TFPRISC(ConstantNode con) {super(con);}
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
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
            _con.print(sb.p(reg).p(" #"));
    }

    @Override public String op() {
        if( _con == Type.NIL )
            return "xor";
        return "ldx";           // Some fancier encoding
    }
}
