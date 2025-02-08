package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.ProjNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class ProjRISC extends ProjNode implements MachNode{
    ProjRISC(ProjNode p) {super(p);}

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return RegMask.EMPTY; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }
}
