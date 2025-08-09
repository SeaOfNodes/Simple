package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.util.SB;

// Special instruction for loading 8 byte constants from the constant pool.

public class Int8RISC extends ConstantNode implements MachNode, RIPRelSize {
    public Int8RISC(ConstantNode con) { super(con); }

    @Override public String op() { return "ld8"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public Int8RISC copy() { return new Int8RISC(this); }

    @Override public byte encSize(int delta) { return 8;  }

    @Override public void encoding( Encoding enc ) {
        short dst  = enc.reg(this);
        short tmp = (short)riscv.T6;
        enc.largeConstant(this,_con, 0, -1);
        // AUIPC dst,#hi20_constant_pool
        enc.add4(riscv.u_type(riscv.OP_AUIPC, tmp, 0));
        // Load dst,[dst+#low12_constant_pool]
        enc.add4(riscv.i_type(riscv.OP_LOAD, dst, 0b11, tmp, 0));
    }
    @Override public RegMask killmap() { return new RegMask(riscv.T6); }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short dst = enc.reg(this);
        short tmp = (short)riscv.T6;
        // AUIPC dst,#hi20_constant_pool
        enc.patch4(opStart  , riscv.u_type(riscv.OP_AUIPC, tmp, delta>>12));
        // Load dst,[dst+#low12_constant_pool]
        enc.patch4(opStart+4, riscv.i_type(riscv.OP_LOAD, dst, 0b11, tmp, delta & 0xFFF));
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" = #"));
    }

}
