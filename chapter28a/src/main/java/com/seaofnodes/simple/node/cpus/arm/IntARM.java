package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

// Integer constants
public class IntARM extends ConstantNode implements MachNode {
    final String _ext;
    IntARM( ConstantNode con, String ext ) { super(con); _ext = ext; }

    @Override public String op() { return "ldi"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public IntARM copy() { return new IntARM(this,_ext); }

    @Override public void encoding( Encoding enc ) {
        if( _ext!=null ) throw Utils.TODO();
        short self = enc.reg(this);
        long x = _con==Type.NIL ? 0 : ((TypeInteger)_con).value();
        Arm64.constant(enc,self,x);
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
