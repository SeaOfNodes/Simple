package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class CallRX86 extends CallNode implements MachNode {
    CallRX86( CallNode call ) { super(call); }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len
            ? x86_64_v2.WMASK          // Function call target
            : x86_64_v2.callInMask(i); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr()));
    }

    @Override public String op() { return "callr"; }
}
