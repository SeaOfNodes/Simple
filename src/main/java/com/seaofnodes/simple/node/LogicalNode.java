package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public abstract class LogicalNode extends Node {
    // Source location for late reported errors
    Parser.Lexer _loc;

    public LogicalNode(Parser.Lexer loc, Node lhs, Node rhs) { super(null, lhs, rhs); _loc = loc; }
    abstract String op();

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.open().n(in(1)).p(op()).n(in(2)).close();
    }


    @Override public Parser.ParseException err() {
        if( in(1)._type.isHigh() || in(2)._type.isHigh() ) return null;
        if( !(in(1)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(1)._type.glb(),_loc);
        if( !(in(2)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(2)._type.glb(), _loc);
        return null;
    }
}
