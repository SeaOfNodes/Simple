package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

// Jump on flags, uses flags
public class JmpX86 extends IfNode implements MachNode, RIPRelSize {
    String _bop;
    boolean _unsigned, _floating, _unorderedTaken;
    JmpX86( IfNode iff, String bop ) {
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
        if( set instanceof SetX86 setx && (cmp instanceof CmpX86 || cmp instanceof CmpIX86 || cmp instanceof CmpMemX86 || cmp instanceof CmpFX86) ) {
            _inputs.set( 1, cmp );
            _bop = setx._bop;
            _unsigned = setx._unsigned;
            _floating = cmp instanceof CmpFX86;
            _unorderedTaken = _bop.equals("!=");
        } else
            throw Utils.TODO();
    }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void negate() {
        _bop = negate(_bop);
        if( _floating ) _unorderedTaken = !_unorderedTaken;
    }

    private int condition() { return x86_64_v2.jumpop(_bop,_unsigned)&15; }

    @Override public void encoding( Encoding enc ) {
        if( in(1)==null && _bop=="!=" ) return;
        // UCOMISD sets CF/ZF/PF, not signed-integer SF/OF. Handle unordered
        // separately, including when layout has negated the original test.
        if( _floating ) {
            if( _unorderedTaken ) enc.add1(0x0F).add1(0x8A).add4(0); // JP target
            else enc.add1(0x7A).add1(0); // JP past the ordered jump
        }
        X86.branch(enc,in(1)==null ? -1 : condition());
        enc.jump(this,cproj(0));
    }

    // Delta is from opcode start, but X86 measures from the end of the 2-byte encoding
    @Override public byte encSize(int delta) {
        if( in(1)==null && _bop=="!=" ) return 0;
        int prefix = !_floating ? 0 : _unorderedTaken ? 6 : 2;
        return (byte)(prefix + X86.branchSize(delta-prefix,in(1)!=null));
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        assert !(in(1)==null && _bop=="!=");
        int prefix = !_floating ? 0 : _unorderedTaken ? 6 : 2;
        if( _floating )
            X86.patchBranch(enc.bits(),opStart,prefix,_unorderedTaken ? delta : opLen,10);
        X86.patchBranch(enc.bits(),opStart+prefix,opLen-prefix,delta-prefix,in(1)==null ? -1 : condition());
    }

    @Override public void asm(CodeGen code, SB sb) {
        if( in(1)!=null ) {     // Never-node
            String src = code.reg(in(1));
            if( src!="flags" ) sb.p(src).p(" ");
        } else if( _bop=="!=" ) {
            sb.p("never");
            return;
        }
        CFGNode prj = cproj(0).uctrlSkipEmpty(); // 0 is True is jump target
        if( !prj.blockHead() ) prj = prj.cfg0();
        sb.p(label(prj));
    }

    @Override public String comment() { return "L"+cproj(1)._nid; }
}
