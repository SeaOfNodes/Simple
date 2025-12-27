package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

// Node carries an extra internal type
public abstract class TypeNode extends Node {
    public Type _con;
    public TypeNode(Type t, Node... nodes) { super(nodes); assert t!=null; _con = t; }
    public TypeNode(TypeNode tn) { super(tn); _con = tn._con; }
    // Copy edges from ideal, but 'this' is a Type-Mach-Node (e.g. MemOpX86) and needs a _con
    public TypeNode(Node ideal, Type con) { super(ideal); _con = con; }

    // Upgrade the internal type
    @Override boolean _upgradeType( HashMap<String,Type> TYPES) {
        Type t = _con.upgradeType(TYPES);
        if( t == _con ) return false;
        unlock();               // Unlock before changing _con
        _con = t;
        return true;
    }

    @Override public boolean eq(Node n) { return _con==((TypeNode)n)._con; }
    @Override int hash() { return _con.hashCode(); }
}
