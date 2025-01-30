package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewX86 extends NewNode implements MachNode {

    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) {  super(nnn); }

    // Register mask allowed on input i, the size
    @Override public RegMask regmap(int i) {
        // Size
        if( i==1 ) return x86_64_v2.RDI_MASK;
        // All the memory alias edges
        return RegMask.EMPTY;
    }
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    // Register mask allowed as a result.  Pointer result in standard calling
    // convention.
    @Override public RegMask outregmap(int i) {
        if( i == 1 ) return x86_64_v2.RET_MASK;
        // All the memory aliases edges
        return RegMask.EMPTY;
    }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public String op() { return "alloc"; }
}
