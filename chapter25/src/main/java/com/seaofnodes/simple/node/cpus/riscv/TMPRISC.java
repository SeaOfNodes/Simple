package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.util.SB;

public class TMPRISC extends ConstantNode implements MachNode, RIPRelSize {
    final String _ext;
    TMPRISC( ConstantNode con, String ext ) { super(con); _ext = ext; }
    @Override public String op() { return "ldp"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TMPRISC copy() { return new TMPRISC(this,_ext); }
    @Override public void encoding( Encoding enc ) {
        if( _ext!=null ) enc.externalData(this,_ext);
        else enc.largeConstant(this,((TypeMemPtr)_con)._obj,0,-1);
        short dst = enc.reg(this);
        // AUIPC dst,#hi20_constant_pool
        enc.add4(riscv.u_type(riscv.OP_AUIPC, dst, 0));
        // addi dst,[dst+#low12_constant_pool]
        enc.add4(riscv.i_type(riscv.OP_IMM, dst, 0, dst, 0));
    }

    @Override public byte encSize(int delta) { return 8; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short dst = enc.reg(this);
        // AUIPC dst,#hi20_constant_pool
        enc.patch4(opStart  , riscv.u_type(riscv.OP_AUIPC, dst, (delta+0x800)>>12));
        // ADDI's low 12 bits are signed; round the AUIPC page accordingly.
        enc.patch4(opStart+4, riscv.i_type(riscv.OP_IMM, dst, 0, dst, delta & 0xFFF));
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
