package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.lang.StringBuilder;


// use SCVTF or FCVTZS
public class CastARM extends MachConcreteNode implements MachNode{
    Type _t;
    CastARM(CastNode cast) {
        super(cast);
        _t = cast._t;
    }
    @Override public RegMask regmap(int i) { assert i==1; return arm.MEM_MASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.MEM_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form
    // General form: "andi  rd = rs1 & imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("cast(").p(_t.str()).p(")").p(in(1)._type.str());
    }

    @Override public String op() { return "cast"; }
}
