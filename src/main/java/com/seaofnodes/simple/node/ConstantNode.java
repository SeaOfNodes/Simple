package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.BAOS;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * A Constant node represents a constant value.
 * <p>
 * Constants have no semantic inputs. However, we set Start as an input to
 * Constants to enable a forward graph walk.  This edge carries no semantic
 * meaning, and it is present <em>solely</em> to allow visitation.
 * <p>
 * The Constant's value is the value stored in it.
 */

public class ConstantNode extends TypeNode {
    public ConstantNode( Type type ) {
        this(type,false);
    }
    protected ConstantNode( Type type, boolean raw ) {
        super(type,new Node[]{CodeGen.CODE._start});
        assert raw || !(type instanceof TypeFunPtr tfp && tfp.isConstant()) :
            "Unique function identities require a Return-linked FunPtrNode";
        _type = type;
    }
    public ConstantNode( ConstantNode con ) { super(con); }
    @Override public Tag serialTag() { return Tag.Con; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        baos.packed2(types.get(_con)); // NPE if fails lookup
    }
    static Node make( BAOS bais, Type[] types)  { return raw(types[bais.packed2()]); }

    // Semantic constant construction through Opto.  A unique internal
    // function identity carries a lifetime edge to the function's Return.
    public static Node make( Type type ) {
        if( type==Type. CONTROL ) return new CtrlNode();
        if( type==Type.XCONTROL ) return new XCtrlNode();
        if( CodeGen.CODE._phase != null &&
            CodeGen.CODE._phase.ordinal() > CodeGen.Phase.Opto.ordinal() )
            return raw(type);
        if( type instanceof TypeFunPtr tfp && tfp.isConstant() ) {
            FunNode fun = CodeGen.CODE.lookupFun(tfp);
            if( fun != null )
                return new FunPtrNode(tfp,CodeGen.CODE._start,fun.ret()).init();
            // TODO: A singleton FIDX can also emerge after merging and
            // guarding several function pointers.  Preserve that identity
            // even if its internal Return is no longer available.
            return raw(type);
        }
        return new ConstantNode(type);
    }

    // Exact construction for deserialization and graph-incomplete machine
    // selection.  No semantic classification, compute, idealize, or GVN.
    public static ConstantNode raw( Type type ) { return new ConstantNode(type,true); }
    public static ConstantNode raw( ConstantNode con ) { return new ConstantNode(con); }

    // Analysis input created from whole cloth, rather than a first-class
    // runtime value.  In particular, a constructor call descriptor does not
    // own the constructor function; the class FunPtr does.
    public static ConstantNode seed( Type type ) { return new ConstantNode(type,true); }

    @Override public String  label() { return "#"+_con; }
    @Override public String glabel() { return "#"+_con.gprint(); }
    @Override public String uniqueName() { return "Con_" + _nid; }
    @Override public Node copy() { return raw(this); }


    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _con instanceof TypeFunPtr tfp && tfp.isConstant() ) {
            FunNode fun = CodeGen.CODE.lookupFun(tfp);
            if( fun!=null && fun._name != null )
                return sb.append("{ ").append(fun._name).append("}");
        }
        return sb.append(_con==null ? "---" : _con.toString());
    }

    @Override public boolean isConst() { return true; }
    @Override public Type compute() { return _con; }
    @Override public Node idealize() { return null; }
}
