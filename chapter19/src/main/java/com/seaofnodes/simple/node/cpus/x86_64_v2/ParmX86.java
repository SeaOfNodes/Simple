package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.ParmNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class ParmX86 extends ParmNode implements MachNode {

    final RegMask _rmask;
    ParmX86( ParmNode parm ) {
        super(parm);
        _rmask = new RegMask(machine().callInArg(_idx));
    }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return RegMask.EMPTY; }
    // Register mask allowed as a result.  Calling convention register
    @Override public RegMask outregmap() { return _rmask; }

    // Encoding is appended into the byte array.  Returns size
    @Override public int encoding(ByteArrayOutputStream bytes) { return 0; }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(SB sb, String comment) {
        throw Utils.TODO();
    }

}
