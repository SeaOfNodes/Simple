package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

public class CallRX86 extends CallNode implements MachNode {
    final TypeFunPtr _tfp;
    CallRX86( CallNode call ) { super(call); _tfp = call.tfp(); }
    @Override public TypeFunPtr tfp() { return _tfp; }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len-1
            ? x86_64_v2.WMASK          // Function call target
            : x86_64_v2.callInMask(tfp(),i); // Normal argument
    }
    @Override public RegMask outregmap() { return x86_64_v2.RET_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr()));
    }

    @Override public String op() { return "callr"; }

}
