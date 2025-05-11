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
        if( set==null ) return; // Never-node cutout
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetARM)
            _inputs.set(1,cmp);
        else
            throw Utils.TODO();
    }

    @Override public RegMask regmap(int i) { assert i==1; return arm.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void negate() { _bop = negate(_bop);  }

    @Override public void encoding( Encoding enc ) {
        // Assuming that condition flags are already set.  These flags are set
        // by comparison (or sub).  No need for regs because it uses flags
        if( in(1)!=null ) {
            // B.cond
            enc.add4( arm.b_cond(arm.OP_BRANCH, 0, arm.make_condition(_bop)) );
        } else {
            if( _bop=="!=" ) return; // Inverted, no code
            enc.add4(arm.b(arm.OP_UJMP, 0));
        }
        enc.jump(this,cproj(0));
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( in(1)==null && _bop=="!=" ) return 0; // Inverted never-node, no code
        if( -(1<<19) <= delta && delta < (1<<19) ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        assert !( in(1)==null && _bop=="!=" ); // Inverted never-node, no code no patch
        if( opLen==4 ) {
            enc.patch4(opStart,arm.b_cond(arm.OP_BRANCH, delta, arm.make_condition(_bop)));
        } else {
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        if( in(1)!=null ) {     // Never-node
            String src = code.reg(in(1));
            if( src!="flags" ) sb.p(src).p(" ");
        } else if( _bop=="!=" ) {
            sb.p("never");
            return;
        }
        CFGNode prj = cproj(0).uctrlSkipEmpty();
        if( !prj.blockHead() ) prj = prj.cfg0();
        sb.p(label(prj));
    }
    @Override public String comment() { return "L"+cproj(1)._nid; }
}
