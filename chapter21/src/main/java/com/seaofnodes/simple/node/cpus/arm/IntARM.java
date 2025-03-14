package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeInteger;

// Integer constants
public class IntARM extends ConstantNode implements MachNode {
    IntARM( ConstantNode con ) { super(con); }

    @Override public String op() { return "ldi"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public IntARM copy() { return new IntARM(this); }

    @Override public void encoding( Encoding enc ) {
        short self = enc.reg(this);
        long x = ((TypeInteger)_con).value();
        if( (short)x == x )
            enc.add4(arm.mov(485, 0, (int)(x&0xFFFF), self));
        else
            throw Utils.TODO("Handle cases bigger than 16 bits");
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" = #"));
    }
}
