package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

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
    @Override public Node copy() {
        FunPtrNode fptr = new FunPtrNode((TypeFunPtr)_con,null,null);
        fptr._type = _type;
        return fptr;
    }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        FunNode fun = CodeGen.CODE._link(((TypeFunPtr)_con).fidx());
        return fun!=null && fun._name!=null
            ? p.p("{ ").p(fun._name).p("}")
            : p.p(_con);
    }

    @Override public boolean isConst() { return true; }
    @Override public Type compute() {
        ReturnNode ret = ret();
        if( ret == null ) return _con;
        if( !(ret._type instanceof TypeTuple tt) ) return ret._type.oob();
        TypeFunPtr tfp = ret.fun().sig();
        // Both the sig and the return value must be true, so JOIN both
        Type tret = tfp._ret.join(tt.ret());
        return tfp.makeFrom(tret);
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
        // TODO: If the function body dies but this value remains live only as
        // a null/equality sentinel, preserve the identity without the Return.
        if( fun.isDead() )
            { setDef(1,null); liftType(sig.makeFrom(Type.TOP)); return this; }
        return null;
    }
}
