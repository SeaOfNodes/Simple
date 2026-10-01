package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;

// Integer constants
public class IntX86 extends ConstantNode implements MachNode {
    IntX86( ConstantNode con ) { super(con); }
    @Override public String op() {
        return _con == Type.NIL || _con == TypeInteger.ZERO ? "xor" : "ldi";
    }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new IntX86(this); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Zero-set uses XOR kills flags
    @Override public RegMask killmap() {
        return _con == Type.NIL || _con == TypeInteger.ZERO ? x86_64_v2.FLAGS_MASK : null;
    }

    @Override public void encoding( Encoding enc ) {
        X86.constant(enc,enc.reg(this),_con==Type.NIL ? 0 : ((TypeInteger)_con).value());
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        if( _con == Type.NIL || _con == TypeInteger.ZERO )
            sb.p(reg).p(",").p(reg);
        else
            _con.print(sb.p(reg).p(" = #"));
    }
}
