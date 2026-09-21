package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import java.lang.StringBuilder;
import java.util.BitSet;

public abstract class MemOpARM extends MemOpNode implements MachNode {
    final int _off;             // Limit 9 bits sized, or (13 bits<<logsize) unsigned
    final int _imm;             // Limit ? bits
    final int _bytes;
    final char _sz;
    MemOpARM(MemOpNode mop, Node ptr, Node idx, int off, int imm) {
        super(mop,mop);
        assert ptr._type instanceof TypeMemPtr && !(ptr instanceof AddNode);
        assert ptr() == ptr;
        _inputs.setX(2, ptr);
        _inputs.setX(3, idx);
        _off = off;
        _imm = imm;
        _bytes = mop instanceof StoreNode st ? st.storeSize() : 1<<_con.log_size();
        _sz = (char)('0'+_bytes);
    }

    // Store-based flavors have a value edge
    MemOpARM( MemOpNode mop, Node ptr, Node idx, int off, int imm, Node val ) {
        this(mop,ptr,idx,off,imm);
        _inputs.setX(4,val);
    }

    @Override public String label() { return op();}
    Node val() { return in(4); } // Only for stores

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public Type compute() { throw new AssertionError("Do not call compute on "+getClass().getSimpleName()); }
    @Override public Node idealize() { throw new AssertionError("Do not call idealize on "+getClass().getSimpleName()); }

    int size() { return _bytes; }

    // Wider mask to store both GPRs and FPRs
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        if( i==1 ) return null; // memory
        if( i==2 ) return arm.RMASK; // ptr/base
        if( i==3 ) return arm.RMASK; // off/index
        if( i==4 ) return size() >= 4 ? arm.MEM_MASK : arm.RMASK; // value
        return null; // Anti-dependence
    }

    // Shared encoding for loads and stores(int and float)
    public void ldst_encode( Encoding enc, int opcode_imm, int opcode_reg, Node xval, int size) {
        short ptr = enc.reg(ptr());
        short off = enc.reg(off());
        short val = enc.reg(xval);
        // The allocated register selects integer vs SIMD/FP encoding, even for
        // bitwise copies. ARM numbers each register bank from zero in the opcode.
        boolean fp = val >= arm.D0;
        assert !fp || size >= 4;
        if( fp ) {
            opcode_imm |= 1<<4;
            opcode_reg |= 1<<5;
            val -= arm.D0;
        }
        int body = off() == null
            ? arm.load_str_imm(opcode_imm, _off, ptr, val, size)
            : arm.indr_adr(opcode_reg, off, arm.STORE_LOAD_OPTION.SXTX, 0, ptr, val);
        enc.add4(body);
    }

    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr())).p("+");
        if( off() != null ) sb.p(code.reg(off()));
        else sb.p(_off);
        return sb.p("]");
    }
}
