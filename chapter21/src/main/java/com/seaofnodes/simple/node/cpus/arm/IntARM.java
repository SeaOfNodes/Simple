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
        int opcode;
        if(x >= 0) {
            // movz
            opcode = 0b110100101;
        } else {
            // movn
            opcode = 0b100100101;
            x = ~x; // invert x
        }
        if( (short)x == x )
            enc.add4(arm.mov(opcode, 0, (int)(x&0xFFFF), self));
        else if((int)x == x) {
            // mov - load low 16 bits
            enc.add4(arm.mov(opcode, 0, (int)(x), self));
            // mov - load high 16 bits
            enc.add4(arm.mov(485, 16, (int)(x), self));
        }
        else {
            enc.add4(arm.mov(opcode, 0, (int)(x), self));

            enc.add4(arm.mov(485, 16, (int)(x), self));

            enc.add4(arm.mov(485, 32, (int)(x), self));

            enc.add4(arm.mov(485, 48, (int)(x), self));
        }
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
