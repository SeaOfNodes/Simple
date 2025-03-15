package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFloat;

//FMOV (scalar, immediate)
//Floating-point move immediate.
public class FloatARM extends ConstantNode implements MachNode {
    FloatARM(ConstantNode con) { super(con); }
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }

    // General int registers
    @Override public RegMask outregmap() { return arm.DMASK; }

    @Override public boolean isClone() { return true; }
    @Override public FloatARM copy() { return new FloatARM(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        short dst = (short)(enc.reg(this) - arm.D_OFFSET);
        double d = ((TypeFloat)_con).value();
        long x = Double.doubleToRawLongBits(d);
        // Any number that can be expressed as +/-n * 2-r,where n and r are integers, 16 <= n <= 31, 0 <= r <= 7.
        //arm.f_mov(30,3,imm8,self);
        throw Utils.TODO();
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

    @Override public String op() {
        return "fld";           // Some fancier encoding
    }
}
