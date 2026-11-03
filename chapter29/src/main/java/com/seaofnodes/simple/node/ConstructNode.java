package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.print.ExprPrinter;

/** The source new boundary. Own fields have been constructed, but an incomplete
 * actual argument keeps this result incomplete. Unlike a callee's merged Return,
 * this transfer describes one allocation site. Erased after source checks. */
public class ConstructNode extends Node {
    public ConstructNode(Node value, Ary<Node> args) {
        super(null,value);
        for( Node arg : args ) addDef(arg);
    }
    @Override public String label() { return "Construct"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("construct(").n(in(1)).p(")"); }
    @Override public Type compute() {
        Type t = in(1)._type;
        if( !(t instanceof TypeMemPtr ptr) ) return t;
        boolean partial = false;
        for( int i = 2; i<nIns(); i++ ) partial |= TypeMemPtr.mayBePartial(in(i)._type);
        return ptr.withPartial(partial);
    }
    @Override public Node idealize() {
        return CodeGen.CODE._phase==CodeGen.Phase.Parse ? null : in(1);
    }
}
