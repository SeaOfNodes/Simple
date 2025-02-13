package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.io.ByteArrayOutputStream;

// Integer constants
public class IntX86 extends ConstantNode implements MachNode {

    IntX86( ConstantNode con ) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new IntX86(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
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
            _con.print(sb.p(reg).p(" #"));
    }

    @Override public String op() {
        if( _con == Type.NIL || _con == TypeInteger.ZERO )
            return "xor";
        return "ldi";           // Some fancier encoding
    }
}
