package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 * A Constant node represents a constant value.
 * <p>
 * Constants have no semantic inputs. However, we set Start as an input to
 * Constants to enable a forward graph walk.  This edge carries no semantic
 * meaning, and it is present <em>solely</em> to allow visitation.
 * <p>
 * The Constant's value is the value stored in it.
 */

public class ConstantNode extends Node {
    public final Type _con;
    public ConstantNode( Type type ) {
        super(new Node[]{CodeGen.CODE._start});
        _con = _type = type;
    }
    public ConstantNode( ConstantNode con ) { super(con);  _con = con._type;  }
    @Override public Tag serialTag() { return Tag.Con; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(types.get(_con)); // NPE if fails lookup
    }
    static Node make( BAOS bais, Type[] types)  { return new ConstantNode(types[bais.packed2()]); }

    public static Node make( Type type ) {
        if( type==Type. CONTROL ) return new CtrlNode();
        if( type==Type.XCONTROL ) return new XCtrlNode();
        return new ConstantNode(type);
    }

    @Override public String  label() { return "#"+_con; }
    @Override public String glabel() { return "#"+_con.gprint(); }
    @Override public String uniqueName() { return "Con_" + _nid; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _con instanceof TypeFunPtr tfp && tfp.isConstant() ) {
            FunNode fun = CodeGen.CODE.link(tfp);
            if( fun!=null && fun._name != null )
                return sb.append("{ ").append(fun._name).append("}");
        }
        return sb.append(_con==null ? "---" : _con.toString());
    }

    @Override public boolean isConst() { return true; }
    @Override public Type compute() { return _con; }
    @Override public Node idealize() { return null; }
    @Override public boolean eq(Node n) { return _con==((ConstantNode)n)._con; }
    @Override int hash() { return _con.hashCode(); }
}
