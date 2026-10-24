package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.*;

/** Publication orders memory; it needs no runtime instruction or register. */
public class EscapeMach extends EscapeNode implements MachNode {
    public EscapeMach(EscapeNode esc) { super(esc); _inputs.set(3,null); }
    @Override public String op() { return label(); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding(Encoding enc) { }
}
