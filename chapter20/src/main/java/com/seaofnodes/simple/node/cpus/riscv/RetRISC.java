package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFloat;

import java.io.ByteArrayOutputStream;

public class RetRISC extends ReturnNode implements MachNode{
    RetRISC(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    // Todo: postSelect
    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        return switch( i ) {
        case 0 -> null;
        case 1 -> null;
        case 2 -> riscv.retMask(_fun.sig());
        case 3 -> riscv.RPC_MASK;
        default -> throw Utils.TODO();
        };
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(2)));
    }

    // Correct Nodes outside the normal edges
    @Override public void postSelect() {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }

    @Override public String op() { return "ret"; }
}
