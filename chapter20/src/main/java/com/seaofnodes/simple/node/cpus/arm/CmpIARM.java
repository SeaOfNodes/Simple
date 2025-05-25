package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Compare with immediate.
// Conditional compare (immediate)? e.g CCMP(immediate)
public class CmpIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    final String _bop;
    CmpIARM(BoolNode bool, TypeInteger ti) {
        super(bool);
        _inputs.pop();
        _bop = bool.op();
        _imm = (int)ti.value();
        assert _imm == ti.value();
    }
    // Copy constructor does not set reverse edges in, because the basic block
    // is changing from the original node - and it must be properly placed in a
    // new block.
    CmpIARM( CmpIARM cmp ) {
        super(cmp);
        _bop = cmp._bop;
        _imm = cmp._imm;
        // While in(0) will be handled by the caller, all other edges must be
        // handled in the constructor.
        cmp.in(1)._outputs.push(this);
    }

    @Override public RegMask regmap(int i) { assert i==1; return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new CmpIARM(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "cmp  rs1, 1"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }
    @Override public String op() { return _imm==0 ? "test" : "cmp"; }

 }
