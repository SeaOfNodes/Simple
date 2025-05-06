package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

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
        X86.lea(enc,enc.reg(this),enc.reg(in(1)),enc.reg(in(2)),_offset,_scale);
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
