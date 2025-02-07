package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

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
    @Override public RegMask regmap(int i) {
        return switch( i ) {
        case 0 -> RegMask.EMPTY;
        case 1 -> RegMask.EMPTY;
        case 2 -> x86_64_v2.RET_MASK;
        case 3 -> RegMask.EMPTY; // RPC is always on stack
        default -> throw Utils.TODO();
        };
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

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

    @Override public String op() { return "ret"; }
}
