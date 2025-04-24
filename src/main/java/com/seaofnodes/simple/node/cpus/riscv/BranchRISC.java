package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;

// Conditional branch such as: BEQ
public class BranchRISC extends IfNode implements MachNode, RIPRelSize {
    String _bop;
    // label is obtained implicitly
    public BranchRISC( IfNode iff, String bop, Node n1, Node n2 ) {
        super(iff);
        _bop = bop;
        _inputs.setX(1,n1);
        _inputs.setX(2,n2);
    }

    @Override public String op() { return "b"+_bop; }
    @Override public String label() { return op(); }
    @Override public String comment() { return "L"+cproj(1)._nid; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void negate() {
        _bop = negate(_bop);
        // Cannot encode ">" or "<=", so flip these
        if( _bop.equals(">") || _bop.equals("<=") ) {
            swap12();           // Cannot negate the test, so swap the operands
            _bop = swap(_bop);  // Swap test to match
        }
    }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("if( ");
        if( in(1)==null ) sb.append("0");
        else in(1)._print0(sb,visited);
        sb.append(_bop);
        if( in(2)==null ) sb.append("0");
        else in(2)._print0(sb,visited);
        return sb.append(" )");
    }

    @Override public void encoding( Encoding enc ) {
        if( in(1)==null && _bop=="!=" ) return; // Inverted never-node, no code
        enc.jump(this,cproj(0));
        if( in(1)==null )       // Never node
            enc.add4(riscv.j_type(riscv.OP_JAL, 0, 0));
        else {
            // Todo: relocs (for offset - immf)
            short src1 = enc.reg(in(1));
            short src2 = in(2)==null ? (short)riscv.ZERO : enc.reg(in(2));
            enc.add4(riscv.b_type(riscv.OP_BRANCH, riscv.jumpop(_bop), src1, src2, 0));
        }
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( in(1)==null && _bop=="!=" ) return 0; // Inverted never-node, no code
        if( -4*1024 <= delta && delta < 4*1024 ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        assert !( in(1)==null && _bop=="!=" ); // Inverted never-node, no code no patch
        if( in(1)==null ) {     // Never node
            enc.patch4(opStart,riscv.j_type(riscv.OP_JAL, 0, delta));
        } else {
            short src1 = enc.reg(in(1));
            short src2 = in(2)==null ? (short)riscv.ZERO : enc.reg(in(2));
            if( opLen==4 ) {
                enc.patch4(opStart,riscv.b_type(riscv.OP_BRANCH, riscv.jumpop(_bop), src1, src2, delta));
            } else {
                throw Utils.TODO();
            }
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        if( in(1)==null && _bop=="!=" ) {
            sb.p("never");
            return;
        }
        String src1 = in(1)==null ? "#0" : code.reg(in(1));
        String src2 = in(2)==null ? "#0" : code.reg(in(2));
        sb.p(src1).p(" ").p(_bop).p(" ").p(src2).p(" ");
        CFGNode prj = cproj(0).uctrlSkipEmpty();
        if( !prj.blockHead() ) prj = prj.cfg0();
        sb.p(label(prj));
    }
}
