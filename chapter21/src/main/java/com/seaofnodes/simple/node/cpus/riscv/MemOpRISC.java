package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.lang.StringBuilder;
import java.util.BitSet;


public abstract class MemOpRISC extends MemOpNode implements MachNode {
    final int _off;             // Limit 12 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpRISC(MemOpNode mop, int off, Node val) {
        super(mop,mop);
        assert mop.ptr()._type instanceof TypeMemPtr && !(base instanceof AddNode);
        _inputs.setX(2, mop.ptr() );
        _inputs.setX(3, null);  // Never an index
        _inputs.setX(4, val );
        _off = off;
    }

    @Override public String label() { return op(); }
    Node val() { return in(4); } // Only for stores

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }


    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        // 1 - memory
        if( i==2 ) return riscv.RMASK;    // base
        // 2 - index
        if( i==4 ) return riscv.MEM_MASK; // value
        throw Utils.TODO();
    }

    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr())).p("+");
        if( idx() != null ) sb.p(code.reg(idx()));
        else sb.p(_off);
        return sb.p("]");
    }
}
