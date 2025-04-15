package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class NewX86 extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) { super(nnn); }
    @Override public void encoding( Encoding enc ) {
        enc.external(this,"calloc");
        // This has to call the *native* ABI, regardless of how Simple is
        // being compiled, because it links against the native calloc.
        // ldi rcx,#1 // number of elements to calloc
        enc.add1(0xB8 + _arg2Reg).add4(1);
        // E8 cd    CALL rel32;
        enc.add1(0xE8);
        enc.add4(0);            // offset
    }
}
