package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class LeaX86 extends MachConcreteNode implements MachNode {
    final int _scale;
    final int _offset;
    LeaX86( Node add, Node base, Node idx, int scale, int offset ) {
        super(add);
        assert scale==0 || scale==1 || scale==2 || scale==3;
        _inputs.pop();
        _inputs.pop();
        _inputs.push(base);
        _inputs.push(idx);
        _scale = scale;
        _offset = offset;
    }

    @Override public String op() { return "lea"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    @Override public void encoding( Encoding enc ) {
        // REX.W + 8D /r	LEA r64,m
        short dst = enc.reg(this);
        short ptr = enc.reg(in(1));
        short idx = enc.reg(in(2));
        // ptr is null
        // just do: [(index * s) + disp32]
        if( ptr == -1 ) {
            enc.add1(x86_64_v2.rex(dst, 0, idx));
            enc.add1(0x8D); // opcode
            enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0x04));
            enc.add1(x86_64_v2.sib(_scale, idx, x86_64_v2.RBP));
            enc.add4(_offset);
            return;
        }

        enc.add1(x86_64_v2.rex(dst, ptr, idx));
        enc.add1(0x8D); // opcode
        // rsp is hard-coded here(0x04)
        x86_64_v2.indirectAdr(_scale, idx, ptr, _offset, dst, enc);
    }

    // General form: "lea  dst = base + 4*idx + 12"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        if( in(1) != null )
            sb.p(code.reg(in(1))).p(" + ");
        sb.p(code.reg(in(2))).p("<<").p(_scale);
        if( _offset!=0 ) sb.p(" + #").p(_offset);
    }
}
