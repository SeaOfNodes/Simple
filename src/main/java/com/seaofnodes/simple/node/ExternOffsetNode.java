package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.BAOS;
import java.util.HashMap;
import java.util.IdentityHashMap;

// A field address supplied by the C linker, not a displacement from the base.
// The class base remains available for ideal type/alias analysis. Instruction
// selection replaces it with this symbol's address and a zero displacement.
public class ExternOffsetNode extends ExternNode {
    public ExternOffsetNode(String name) { super(TypeInteger.BOT,name); }
    @Override public Tag serialTag() { return Tag.ExternOffset; }
    @Override public Node copy() { return new ExternOffsetNode(_extern); }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> nodes) {
        baos.packed2(strs.get(_extern));
    }
}
