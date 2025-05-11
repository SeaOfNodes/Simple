package com.seaofnodes.simple.node;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;

/**
 * Cast a pointer to read-only
 */
public class ReadOnlyMach extends ReadOnlyNode implements MachNode {
    public ReadOnlyMach( ReadOnlyNode n ) { super(n); }
    @Override public String op() { return label(); }
    @Override public RegMask regmap(int i) { assert i==1; return RegMask.FULL; }
    @Override public RegMask outregmap() { return RegMask.FULL; }
    @Override public int twoAddress( ) { return 1; }
    @Override public void encoding( Encoding enc ) { }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(in(1))); }
}
