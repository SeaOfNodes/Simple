package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class TFPARM extends FunPtrNode implements MachNode, RIPRelSize {
    final String _ext;
    // Pointer to a Simple function
    TFPARM( FunPtrNode fptr ) { super(fptr); _ext = null; }
    // Pointer to an Extern "C" function
    TFPARM( TypeFunPtr fptr, String ext ) { super(fptr,CodeGen.CODE._start,null); _type = fptr; _ext = ext; }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPARM copy() { return new TFPARM((TypeFunPtr)_con,_ext); }
    @Override public void encoding( Encoding enc ) {
        if( _ext==null ) enc.relo(this);          // Internal patch
        else             enc.external(this,_ext); // ELF-file external patch
        short dst = enc.reg(this);
        // adrp    x0, 0
        enc.add4(arm.adrp(1,0, arm.OP_ADRP, 0,dst));
        // add     x0, x0, 0
        arm.imm_inst(enc,arm.OPI_ADD,0, dst);
    }

    @Override public byte encSize(int delta) {
        return 8;
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        if(opLen == 8 ) {
            arm.patch_adrp_add(enc, opStart, delta, rpc);
        } else {
            // should not happen as one instruction is 4 byte, and TFP arm encodes 2.
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _type.print(sb.p(reg).p(" #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
