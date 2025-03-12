package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;

// Store memory addressing on ARM
// Support imm, reg(direct), or reg+off(indirect) addressing
// Base = base - base pointer, offset is added to base
// idx  = null
// off  = off - offset added to base)
// imm  = imm - immediate value to store
// val  = Node of immediate value to store(null if its a constant immediate)

//e.g s.cs[0] =  67; // C
// base = s.cs, off = 4, imm = 67, val = null

// sw rs2,offset(rs1)
public class StoreRISC extends MemOpRISC {
    StoreRISC( StoreNode st, Node base, int off, Node idx, Node val ) {
        super(st, base, idx, off, val);
    }

    // Wider mask to store both GPRs and FPRs
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        // 1 - mem
        if( i==2 ) return riscv.RMASK; // ptr
        // 2 - index
        if( i==4 ) return riscv.MEM_MASK;
        throw Utils.TODO();
    }


    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
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

    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p("0");
        else sb.p(code.reg(val()));
    }

    @Override public String op() { return "st"+_sz; }
}
