package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

public abstract class MemOpRISC extends MemOpNode implements MachNode {
    final int _off;             // Limit 12 bits
    final char _sz;
    MemOpRISC(MemOpNode mop, Node base, int off, Node val) {
        super(mop,mop);
        assert base._type instanceof TypeMemPtr;
        _inputs.setX(2, base ); // Base can be an Add, is no longer raw object base
        _inputs.setX(3, null);  // Never an index
        _inputs.setX(4, val );
        _off = off;
        _sz = (char)('0'+(1<<_con.log_size()));
    }

    @Override public String label() { return op(); }
    Node val() { return in(4); } // Only for stores

    @Override public StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }

    // func3 is based on load/store size and extend
    abstract int func3();

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
        return sb.p(_off).p("]");
    }
}
