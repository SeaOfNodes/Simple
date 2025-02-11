package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;


public class MemOpRISC extends MemOpNode implements MachNode{
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpRISC(Node op, MemOpNode mop) {
        super(op, mop);
    }
    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public String label() { return op(); }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    @Override public int encoding(ByteArrayOutputStream bytes) { throw Utils.TODO(); }


    SB asm_address(CodeGen code, SB sb) {
       return sb;
    }
}
