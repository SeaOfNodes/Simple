package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

// Return
public class RetX86 extends ReturnNode implements MachNode {
    RetX86( ReturnNode ret, FunX86 fun ) { super(ret, fun); fun.setRet(this); }

    // Register mask allowed on input i.  0 for no register.
    @Override public long regmap(int i) {
        //return 0;
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public long outregmap() { throw Utils.TODO(); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // Human readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(SB sb, String comment) {
        throw Utils.TODO();
    }

}
