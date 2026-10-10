package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

/** An active class touch, retained in control until initialization validation.
 *  This checkpoint records the obligation only; flag/call lowering comes later.
 */
public class CLInitNode extends CFGNode {
    public final Parser.Lexer _loc;
    public final String _field;

    public CLInitNode(Parser.Lexer loc, String field, Node ctrl, Node ptr) {
        super(ctrl,ptr);
        _loc=loc;
        _field=field;
    }

    public Node ptr() { return in(1); }
    @Override public String label() { return "CLInit"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("CLInit ").p(ptr()); }
    @Override public Type compute() { return in(0)._type; }
    @Override public Node idealize() {
        // A receiver discovered to be an ordinary instance needs no class init.
        if( ptr()._type instanceof TypeMemPtr ptr && ptr._obj!=TypeStruct.BOT &&
            !ptr._obj.isHigh() && !Parser.startsClzPrefix(ptr._obj._name) ) return in(0);
        return null;
    }
}
