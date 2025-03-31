package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Jump on flags, uses flags
public class BranchARM extends IfNode implements MachNode, RIPRelSize {
    String _bop;
    BranchARM(IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }
    @Override public String op() { return "j"+_bop; }
    @Override public String label() { return op(); }

    @Override public void postSelect(CodeGen code) {
        Node set = in(1);
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetARM)
            _inputs.set(1,cmp);
        else
            throw Utils.TODO();
    }

    @Override public RegMask regmap(int i) { assert i==1; return arm.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void invert() { _bop = invert(_bop);  }

    @Override public void encoding( Encoding enc ) {
        // Assuming that condition flags are already set.  These flags are set
        // by comparison (or sub).  No need for regs because it uses flags
        enc.jump(this,cproj(0));
        // B.cond
        enc.add4( arm.b_cond(arm.OP_BRANCH, 0, arm.make_condition(_bop)) );
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( -(1<<19) <= delta && delta < (1<<19) ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        if( opLen==4 ) {
            enc.patch4(opStart,arm.b_cond(arm.OP_BRANCH, delta, arm.make_condition(_bop)));
        } else {
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="flags" ) sb.p(src).p(" ");
        CFGNode prj = cproj(0);
        while( prj.nOuts() == 1 )
            prj = prj.uctrl();  // Skip empty blocks
        sb.p(label(prj));
    }
    @Override public String comment() { return "L"+cproj(1)._nid; }
}
