package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.LoadRISC;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.*;
import java.io.ByteArrayOutputStream;
import java.lang.StringBuilder;
import java.util.BitSet;
import java.util.Optional;


public abstract class MemOpARM extends MemOpNode implements MachNode {
    final int _off;             // Limit 32 bits
    final int _imm;             // Limit 32 bits
    final char _sz = (char)('0'+(1<<_declaredType.log_size()));
    MemOpARM(MemOpNode mop, Node base, Node idx, int off, int imm) {
        super(mop,mop);
        assert base._type instanceof TypeMemPtr && !(base instanceof AddNode);
        assert ptr() == base;
        _inputs.setX(2, base);
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
        if(i == 1) return null;
        if(i == 2) return arm.RMASK; // base
        if(i == 4) return arm.MEM_MASK; // value
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() {
        throw Utils.TODO();
    }

    @Override public int encoding(ByteArrayOutputStream bytes) {
        switch(this) {
            case LoadARM loadARM -> {
                LRG load_rg = CodeGen.CODE._regAlloc.lrg(this);
                short load_reg = load_rg.get_reg();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                assert base_rg != null; // base always must be provided
                short base_reg = base_rg.get_reg();

                LRG index_rg = CodeGen.CODE._regAlloc.lrg(in(3));
                short index_reg = -1;

                if(index_rg != null) {
                    index_reg = index_rg.get_reg();
                }

                int beforeSize = bytes.size();
                // load from memory into load_reg
                int body;
                if(_off == 0) {
                    // ldr(reg)
                    if(index_reg != -1) {
                        arm.STORE_LOAD_OPTION reg_op =  arm.STORE_LOAD_OPTION.SXTW;
                        body = arm.indr_adr(1987,  index_reg, reg_op, 0, base_reg, load_reg);
                    } else {
                        // if index is not specified then just base + 0(offset)
                        body = arm.load_str_imm(1986, _off, base_reg, load_reg);
                    }

                } else {
                    // ldr(imm)
                    body = arm.load_str_imm(1986, _off, base_reg, load_reg);
                }

                arm.push_4_bytes(body, bytes);

                return  bytes.size() - beforeSize;

            }
            case StoreARM storeARM -> {
                LRG store_rg = CodeGen.CODE._regAlloc.lrg(in(4));
                // store reg is never ull
                short store_reg = store_rg.get_reg();

                LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(2));
                short base_reg = base_rg.get_reg();

                LRG index_rg = CodeGen.CODE._regAlloc.lrg(in(3));
                short index_reg = -1;

                if(index_rg != null) {
                    index_reg = index_rg.get_reg();
                }

                int beforeSize = bytes.size();
                // store store_reg into memory
                int body;
                if(_off == 0) {
                    // if index is specified
                    if(index_reg != -1) {
                        arm.STORE_LOAD_OPTION reg_op = arm.STORE_LOAD_OPTION.SXTW;
                        body = arm.indr_adr(1985, index_reg, reg_op, 0, base_reg, store_reg);
                    } else {
                        // if index is not specified then just base + 0(offset)
                        body = arm.load_str_imm(1984, _off, base_reg, store_reg);
                    }

                } else {
                    // base + offset
                    body = arm.load_str_imm(1984, _off, base_reg, store_reg);
                }
                arm.push_4_bytes(body, bytes);
                return  bytes.size() - beforeSize;
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
