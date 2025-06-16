package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import java.util.BitSet;
import java.util.HashMap;

/**
   A constant with external linkage.
 */

public class ExternNode extends ConstantNode {
    public final String _extern;
    public ExternNode(Type t, String ex) { super(t); _extern = ex; }
    @Override public Tag serialTag() { return Tag.Extern; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(types.get(_con   ));
        baos.packed2( strs.get(_extern));
    }
    static Node make( BAOS bais, String[] strs, Type[] types)  {
        return new ExternNode(types[bais.packed2()], strs[bais.packed2()]);
    }

    @Override public String  label() { return "#"+_con+":"+_extern; }
    @Override public String glabel() { return _con.print(new SB().p("#"), new BitSet(), true).p(":").p(_extern).toString(); }
    @Override public String uniqueName() { return "Extern_" + _nid; }

    @Override public boolean eq(Node n) { return this==n; }
    @Override public void gather( HashMap<String,Integer> strs ) {
        Serialize.gather(strs,_extern);
    }
}
