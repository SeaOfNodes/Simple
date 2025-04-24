package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

/**
 * A Constant node represents a constant value.  At present, the only constants
 * that we allow are integer literals; therefore Constants contain an integer
 * value.  As we add other types of constants, we will refactor how we represent
 * Constants.
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
    public ConstantNode( Node con, Type t ) { super(con);  _con = t;  }
    public ConstantNode( ConstantNode con ) { this(con,con._type);  }

    public static Node make( Type type ) {
        if( type==Type. CONTROL ) return new CtrlNode();
        if( type==Type.XCONTROL ) return new XCtrlNode();
        return new ConstantNode(type);
    }

    @Override protected String repeatName() {
        return _con==null || _con.toString().length()<=32 ? null : uniqueName();
    }

    @Override public String label() { return "Con"; }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( _con instanceof TypeFunPtr tfp && tfp.isConstant() && tfp.notNull() ) {
            FunNode fun = CodeGen.CODE.link(tfp);
            if( fun!=null && fun._name != null )
                return p.p("{ ").p(fun._name).p("}");
        }
        return p.p(_con==null ? "---" : _con.print(new SB()));
    }

    @Override public boolean isConst() { return true; }

    @Override
    public Type compute() { return _con; }

    @Override
    public Node idealize() { return null; }

    @Override
    public boolean eq(Node n) {
        ConstantNode con = (ConstantNode)n; // Contract
        return _con==con._con;
    }

    @Override
    int hash() { return _con.hashCode(); }
}
