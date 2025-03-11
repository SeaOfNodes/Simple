package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.Type;

public class TFPRISC extends ConstantNode implements MachNode{
    TFPRISC(ConstantNode con) {super(con);}
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return riscv.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public TFPRISC copy() { return new TFPRISC(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // load function pointer into a reg
        // addi x2, x1, m
        // x1 is the base address.
        // m is the immediate offset.
        // x2 will hold the computed address.

        LRG tfp_rg = CodeGen.CODE._regAlloc.lrg(this);

        //TODO: relocs
        short rd = tfp_rg.get_reg();
        // assume x2 = x1 are same for now
        int beforeSize = bytes.size();

        int body = riscv.i_type(riscv.I_TYPE, rd, 0, rd, 0);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        if( _con == Type.NIL )
            sb.p(reg).p(",").p(reg);
        else
            _con.print(sb.p(reg).p(" #"));
    }

    @Override public String op() {
        if( _con == Type.NIL )
            return "xor";
        return "ldx";           // Some fancier encoding
    }
}
