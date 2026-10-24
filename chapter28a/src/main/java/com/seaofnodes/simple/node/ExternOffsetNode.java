package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.HashMap;
import java.util.IdentityHashMap;

// A field address supplied by the C linker, not a displacement from the base.
// The class base remains available for ideal type/alias analysis. Instruction
// selection replaces it with this symbol's address and a zero displacement.
public class ExternOffsetNode extends ExternNode {
    public ExternOffsetNode(String name) { super(TypeInteger.BOT,name); }

    @Override public Node copy() { return new ExternOffsetNode(_extern); }

}
