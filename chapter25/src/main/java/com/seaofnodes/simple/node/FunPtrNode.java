package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.BitSet;
import java.util.HashMap;

// Function Pointer
public class FunPtrNode extends Node {
    int _fidx;
    public FunPtrNode(ReturnNode ret, int fidx) { super( null,ret );  _fidx=fidx; }

    public FunPtrNode(FunPtrNode c) {
        super(c);               // Call parent copy constructor
        _fidx = c._fidx;
    }
    @Override public Tag serialTag() { return Tag.FunPtr; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(_fidx);
    }
    static Node make( BAOS bais)  {
        return new FunPtrNode(null,bais.packed2());
    }

    public ReturnNode ret() {
        return in(1) instanceof ReturnNode ret ? ret : null;
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        ReturnNode ret = ret();
        if( ret!=null )
            return sb.append(ret.fun()._name == null ? ret.fun().sig().toString() : "{ "+ret.fun()._name+"}" );
        return sb.append( "*$fun" ).append( _fidx );
    }


    @Override
    public Type compute() {
        ReturnNode ret = ret();
        if( ret==null )
            return TypeFunPtr.TOP;
        if( ret.fun().sig().fidx() != _fidx )
            return TypeFunPtr.TOP;
        return ret.fun().sig();
    }

    @Override
    public Node idealize() {
        return null;
    }

    void setNewFIDX( ) {
        unlock();
        TypeFunPtr sig = ret().fun().sig();
        _type = sig;
        _fidx = sig.fidx();
    }


    // Acts like a constant, sometimes.
    @Override public boolean isConst() { return true; }

    @Override public boolean eq(Node n) { return _fidx==((FunPtrNode)n)._fidx; }
    @Override int hash() { return _fidx; }
    @Override public Node copy() { return new FunPtrNode(this); }
}
