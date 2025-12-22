package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.SB;

/**
 * Cast a pointer to read-only
 */
public class CastMach extends CastNode implements MachNode {
    public CastMach( CastNode n ) { super(n); }
    @Override public String op() { return label(); }
    @Override public RegMask regmap(int i) { assert i==1; return RegMask.FULL; }
    @Override public RegMask outregmap() { return RegMask.FULL; }
    @Override public int twoAddress( ) { return in(1)._type==Type.BOTTOM ? 0 : 1; }
    @Override public void encoding( Encoding enc ) { }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(in(1))); }
}
