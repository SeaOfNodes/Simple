package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

import java.io.ByteArrayOutputStream;

public class CallRRARM extends CallNode implements MachNode{
    CallRRARM(CallNode call) {super(call);}

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        // Todo: float or int?
        return i==_inputs._len
                ? arm.RMASK          // Function call target
                : arm.callInMask(i); // Normal argument
    }
    @Override public RegMask outregmap() { return arm.RET_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr()));
    }

    @Override public String op() { return "callr"; }
}
