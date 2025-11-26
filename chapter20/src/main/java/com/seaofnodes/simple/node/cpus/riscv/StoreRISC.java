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
    StoreRISC( StoreNode st, Node base, Node idx, int off, int imm, Node val ) {
        super(st, base, idx, off, imm, val);
    }

    // Wider mask to store both GPRs and FPRs
    @Override public RegMask regmap(int i) {
        if( i==1 ) return riscv.MEM_MASK;
        if( i==2 ) return riscv.RMASK;
        if( i==3 ) return riscv.RMASK;
        if( i==4 ) return riscv.RMASK;
        throw Utils.TODO();
    }


    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }

    @Override public String op() { return "st"+_sz; }
}
