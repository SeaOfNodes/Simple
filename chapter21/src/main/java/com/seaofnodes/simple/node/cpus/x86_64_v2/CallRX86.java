package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.node.MachNode;

public class CallRX86 extends CallNode implements MachNode {
    CallRX86( CallNode call ) { super(call); }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len
            ? x86_64_v2.WMASK          // Function call target
            : x86_64_v2.callInMask(tfp(),i); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        bytes.write(0xe8);
        int beforeSize = bytes.size();
        // address
        //TODO: relocs
        bytes.write(0x00);
        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr())).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

    @Override public String op() { return "callr"; }
}
