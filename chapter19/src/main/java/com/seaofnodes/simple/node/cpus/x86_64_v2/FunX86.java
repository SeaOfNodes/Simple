package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class FunX86 extends FunNode implements MachNode {

    FunX86( FunNode fun ) {
        super(fun);
    }

    // Register mask allowed on input i.  0 for no register.
    @Override public long regmap(int i) { return 0; }
    // Register mask allowed as a result.  0 for no register.
    @Override public long outregmap() { return 0; }
    // Multi-reg-defining machine op; idx comes from Proj.
    // Sometimes these Projs have no uses, and just exist to kill a register.
    @Override public long outregmap(int idx) {
        throw Utils.TODO();
    }

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
