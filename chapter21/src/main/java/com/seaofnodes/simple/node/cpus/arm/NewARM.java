package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NewARM extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    @Override public void encoding( Encoding enc ) {
        // bl
        enc.external(this,"calloc").add4(arm.b(arm.OP_CALL, 0));
    }
}
