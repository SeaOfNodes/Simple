package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public abstract class LogicalNode extends Node {
    // Source location for late reported errors
    Parser.Lexer _loc;

    public LogicalNode(Parser.Lexer loc, Node lhs, Node rhs) { super(null, lhs, rhs); _loc = loc; }
    abstract String op();

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }


    @Override public Parser.ParseException err() {
        if( in(1)._type.isHigh() || in(2)._type.isHigh() ) return null;
        if( !(in(1)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(1)._type,_loc);
        if( !(in(2)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+op()+"' " + in(2)._type,_loc);
        return null;
    }
}
