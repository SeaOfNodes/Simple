package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NewARM extends NewNode implements MachNode, RIPRelSize {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    @Override public void encoding( Encoding enc ) {
        // BL(branch with link)
        enc.external(this,"calloc").add4(arm.b(arm.OP_CALL, 0));
    }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public byte encSize(int delta) { return 4; }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public void patch(Encoding enc, int opStart, int opLen, int delta ) {
            // delta is always aligned for ARM
            enc.patch4(opStart, arm.b_calloc(arm.OP_CALL, delta));
    }
}
