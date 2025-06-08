package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import java.util.BitSet;

/**
   A constant with external linkage.
 */

public class ExternNode extends ConstantNode {
    public final String _extern;
    public ExternNode(Type t, String ex) { super(t); _extern = ex; }

    @Override public String  label() { return "#"+_con+":"+_extern; }
    @Override public String glabel() { return _con.print(new SB().p("#"), new BitSet(), true).p(":").p(_extern).toString(); }
    @Override public String uniqueName() { return "Extern_" + _nid; }

    @Override public boolean eq(Node n) { return this==n; }
}
