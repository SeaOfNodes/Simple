package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.LoadX86;
import com.seaofnodes.simple.node.cpus.x86_64_v2.StoreX86;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;


public class MemOpRISC extends MemOpNode implements MachNode {
    final int _off;             // Limit 32 bits     // Limit 32 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpRISC(MemOpNode mop, Node base, Node idx, int off, Node val) {
        super(mop,mop);
        assert base._type instanceof TypeMemPtr && !(base instanceof AddNode);
        assert ptr() == base;
        _inputs.setX(2, base);
        _inputs.setX(3,idx);
        _inputs.setX(4,val);
        _off = off;
    }


    Node idx() { return in(3); }
    Node val() { return in(4); } // Only for stores, including op-to-memory

    @Override public  StringBuilder _printMach(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override public String label() { return op(); }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }


    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        if(i == 1) return null;
        if(i == 2) return riscv.RMASK; // base
        if(i == 4) return riscv.MEM_MASK; // value
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    @Override public int encoding(ByteArrayOutputStream bytes) {
        switch (this) {
            case LoadRISC loadRISC -> {
                // ld x2, 0(x3)  # Load the 64-bit value from memory address in x3 into x2
                // The LD instruction loads a 64-bit value from memory into register rd for RV64I.
                // no imm
                // ld	s0,16(sp)
                // i type
                LRG load_rg = CodeGen.CODE._regAlloc.lrg(this);
                short reg = load_rg.get_reg();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                short base_reg = base_rg.get_reg();

                int beforeSize = bytes.size();

                int body = riscv.i_type(0x3,  reg, 0x3, base_reg, _off);
                riscv.push_4_bytes(body, bytes);

                return  bytes.size() - beforeSize;
            }
            case StoreRISC storeRISC -> {
                // can't store imm, must be loaded into reg first
                // store rs2 into rs1 + offset
                LRG store_rg = CodeGen.CODE._regAlloc.lrg(in(4));
                int store_reg = riscv.ZERO;
                if(store_rg != null) store_reg = store_rg.get_reg();

                int beforeSize = bytes.size();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                short base_reg = base_rg.get_reg();

                // check offset here(if they fit into first offset1 - 5 bits)
                boolean short_off = _off >= -16 && _off <= 15;
                int strippedValue = (_off << 5) & ~0x1F;
                int body = riscv.s_type(0x23, short_off ? _off : _off & 0x1F, 0x3, base_reg, store_reg, strippedValue);

                riscv.push_4_bytes(body, bytes);

                return bytes.size() - beforeSize;
            }
            default -> {
            }
        }
        throw Utils.TODO();
    }


    SB asm_address(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr())).p("+");
        if( idx() != null ) sb.p(code.reg(idx()));
        else sb.p(_off);
        return sb.p("]");
    }
}
