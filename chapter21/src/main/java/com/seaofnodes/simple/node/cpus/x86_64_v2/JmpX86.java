package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Jump on flags, uses flags
public class JmpX86 extends IfNode implements MachNode, RIPRelSize {
    String _bop;
    JmpX86( IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }
    @Override public String op() { return "j"+_bop; }
    @Override public String label() { return op(); }
    @Override public void postSelect(CodeGen code) {
        Node set = in(1);
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetX86 setx && (cmp instanceof CmpX86 || cmp instanceof CmpIX86 || cmp instanceof CmpMemX86 || cmp instanceof CmpFX86) ) {
            _inputs.set( 1, cmp );
            _bop = setx._bop;
        } else
            throw Utils.TODO();
    }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }
    @Override public void invert() { _bop = invert(_bop);  }

    @Override public void encoding( Encoding enc ) {
        enc.jump(this,cproj(0));
        int op = x86_64_v2.jumpop(_bop);
        enc.add1(op-16);      // Short form jump
        enc.add1(0);          // Offset
    }

    // Delta is from opcode start, but X86 measures from the end of the 2-byte encoding
    @Override public byte encSize(int delta) { return (byte)(x86_64_v2.imm8(delta-2) ? 2 : 6); }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        byte[] bits = enc.bits();
        if( opLen==2 ) {
            assert bits[opStart] == x86_64_v2.jumpop(_bop)-16;
            delta -= 2;         // Offset from opcode END
            assert (byte)delta==delta;
            bits[opStart+1] = (byte)delta;
        } else {
            assert bits[opStart] == x86_64_v2.jumpop(_bop)-16;
            delta -= 6;         // Offset from opcode END
            bits[opStart] = 0x0F;
            bits[opStart+1] = (byte)x86_64_v2.jumpop(_bop);
            enc.patch4(opStart+2,delta);
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="flags" ) sb.p(src).p(" ");
        CFGNode prj = cproj(0).uctrlSkipEmpty(); // 0 is True is jump target
        if( !prj.blockHead() ) prj = prj.cfg0();
        sb.p(label(prj));
    }

    @Override public String comment() { return "L"+cproj(1)._nid; }
}
