package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;


public class MemOpARM extends MemOpNode implements MachNode {
    final int _off;             // Limit 32 bits
    final int _imm;             // Limit 32 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpARM(MemOpNode mop, Node base, Node idx, int off, int imm) {
        super(mop,mop);
        assert base._type instanceof TypeMemPtr && !(base instanceof AddNode);
        assert ptr() == base;
        _inputs.setX(3,idx);
        _off = off;
        _imm = imm;
    }

    // Store-based flavors have a value edge
    MemOpARM( MemOpNode mop, Node base, Node idx, int off, int imm, Node val ) {
        this(mop,base,idx,off,imm);
        _inputs.setX(4,val);
    }

    Node idx() { return in(3); }
    Node val() { return in(4); } // Only for stores, includin

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
        sb.p("[").p(code.reg(ptr())).p("+");
        if( idx() != null ) sb.p(code.reg(idx()));
        else sb.p(_off);
        return sb.p("]");
    }
}
