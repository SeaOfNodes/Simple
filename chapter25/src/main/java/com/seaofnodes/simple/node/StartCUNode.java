package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;

import java.util.HashMap;
import java.util.IdentityHashMap;

// Start of a single compilation unit, distinct from the outer whole-program Start.
public class StartCUNode extends StartNode {
    public final String _fname;
    public StartCUNode(StartNode start, StopCUNode stop, Type arg, String fname) { super(start,stop,arg);  _fname = fname;  }
    public StartCUNode(StartCUNode start) { super(start); _fname = start._fname; }
    @Override public Tag serialTag() { return Tag.StartCU; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes ) {
        baos.packed2(types.get(_arg));
        baos.packed2(strs.get(_fname));
    }
    static Node make( BAOS bais, String[] strs, Type[] types )  {
        return new StartCUNode(null,null,types[bais.packed2()],strs[bais.packed2()]);
    }

    @Override public CFGNode cfg0() { return (CFGNode)in(0); }

    // IDom is cfg0() and depth is always Start+1 which is 0+1 or 1
    @Override public int idepth() { return CodeGen.CODE.iDepthAt(1); }
    @Override public CFGNode idom(Node dep) { return cfg0(); }
    @Override public void gather( HashMap<String,Integer> strs ) {
        Serialize.gather(strs,_fname);
    }
}
