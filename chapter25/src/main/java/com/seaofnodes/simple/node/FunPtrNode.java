package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

// Constant function pointer whose type follows the callee signature.
public class FunPtrNode extends TypeNode {
    public FunPtrNode(TypeFunPtr tfp, StartNode start, ReturnNode ret) {
        super(tfp,start,ret);
    }
    public FunPtrNode(FunPtrNode fptr) { super(fptr); }
    @Override public Tag serialTag() { return Tag.FunPtr; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes) {
        baos.packed2(types.get(_con));
    }
    static Node make(BAOS bais, Type[] types) {
        return new FunPtrNode((TypeFunPtr)types[bais.packed2()],null,null);
    }
    public ReturnNode ret() { return (ReturnNode)in(1); }
    public FunNode fun() { return ret().fun(); }

    @Override public String label() { return "#"+_con; }
    @Override public String glabel() { return "#"+_con.gprint(); }
    @Override public String uniqueName() { return "FunPtr_" + _nid; }
    @Override public Node copy() {
        FunPtrNode fptr = new FunPtrNode((TypeFunPtr)_con,null,null);
        fptr._type = _type;
        return fptr;
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        FunNode fun = CodeGen.CODE.link(((TypeFunPtr)_con).fidx());
        return fun!=null && fun._name!=null
            ? sb.append("{ ").append(fun._name).append("}")
            : sb.append(_con);
    }

    @Override public boolean isConst() { return true; }
    @Override public boolean isPinned() { return true; }
    @Override public Type compute() {
        ReturnNode ret = ret();
        return ret == null ? _con : ret.fun().sig();
    }
    @Override public Node idealize() {
        ReturnNode ret = ret();
        if( ret==null ) return null;
        // Function died (never executed), but fcn ptr is alive.
        // Can be checked for null, or for unequals another FunPtr.
        FunNode fun = addDep(ret.fun());
        TypeFunPtr sig = fun.sig();
        if( sig != _con )
            { liftType(sig);  return this; }
        if( fun.isDead() )
            { setDef(1,null); liftType(sig.makeFrom(Type.TOP)); return this; }
        return null;
    }
}
