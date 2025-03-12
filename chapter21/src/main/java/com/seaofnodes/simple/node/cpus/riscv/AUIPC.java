package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeInteger;

// Add upper 20bits to PC.  Immediate comes from the relocation info.
public class AUIPC extends ConstantNode implements MachNode {
    AUIPC(ConstantNode con) { super(con); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public AUIPC copy() { return new AUIPC(this); }
    @Override public String op() { return "auipc"; }
    @Override public void encoding( Encoding enc ) {
        enc.call(this);
        short dst = enc.reg(this);
        int auipc = riscv.u_type(0x17, dst, 0);
        enc.add4(auipc);
    }

    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" = PC+#"));
    }
}
