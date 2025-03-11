package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;

// Return
public class RetX86 extends ReturnNode implements MachNode {
    RetX86( ReturnNode ret, FunNode fun ) { super(ret, fun); fun.setRet(this); }

    // Correct Nodes outside the normal edges
    @Override public void postSelect() {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) { return x86_64_v2.retMask(_fun.sig(),i); }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Without imm form
        // Near return
        int beforeSize = bytes.size();
        bytes.write(0xC3); // opcode

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() )
            // Prints return reg (either RAX or XMM0), RPC (always [rsp-4]) and
            // then the callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i))).p("  ");
    }

    @Override public String op() { return "ret"; }
}
