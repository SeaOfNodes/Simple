package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Compare with immediate.
// CMP (immediate) - SUBS
public class CmpIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    final String _bop;
    CmpIARM(BoolNode bool, int imm) {
        super(bool);
        _inputs.pop();
        _bop = bool.op();
        _imm = imm;
    }
    CmpIARM( CmpIARM cmp ) {
        super((Node)null);
        addDef(null);
        addDef(cmp.in(1));
        _bop = cmp._bop;
        _imm = cmp._imm;
    }
    @Override public String op() { return _imm==0 ? "test" : "cmp"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new CmpIARM(this); }

    @Override public void encoding( Encoding enc ) { arm.imm_inst_subs(enc,in(1),in(1), arm.OPI_CMP,_imm); }

    // General form: "cmp  rs1, 1"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }
 }
