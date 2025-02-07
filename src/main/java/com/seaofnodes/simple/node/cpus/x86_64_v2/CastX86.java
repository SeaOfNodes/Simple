package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.CastNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class CastX86 extends CastNode implements MachNode {
    CastX86( CastNode cast ) { super(cast); }
    @Override public RegMask regmap(int i) { assert i==1; return RegMask.FULL; }
    @Override public RegMask outregmap() { return RegMask.FULL; }
    @Override public int twoAddress( ) { return 1; }
    @Override public int encoding(ByteArrayOutputStream bytes) { return 0; /*no encoding*/ }
    @Override public void asm(CodeGen code, SB sb) { _t.print(sb.p(code.reg(in(1))).p(" isa ")); }
    @Override public String op() { return label(); }
}
