package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.Ary;

abstract class TypeCheck {
    static void run(CodeGen code) {
        Ary<Node> errs = new Ary<>(Node.class);
        code._stop.walk(n -> {
            if( n.err()!=null ) errs.add(n);
            return null;
        });
        if( errs.isEmpty() ) return;

        // A bad Load now produces BOTTOM. Report the bad access before a
        // Convert or arithmetic use that merely received its failed result.
        Node first = errs.at(0);
        for( Node n : errs )
            if( noBottomInputs(n) ) { first=n; break; }
        for( Node n : errs )
            if( noBottomInputs(n) &&
                ((n instanceof MemOpNode nm && first instanceof MemOpNode fm &&
                  nm._loc!=null && nm._loc.before(fm._loc)) ||
                 (n instanceof CFGNode nc && first instanceof CFGNode fc && nc.idepth()<fc.idepth())) )
                first=n;
        Parser.ParseException err = first.err();
        if( code._obs!=null ) code._obs.error(first,err.getMessage());
        throw err;
    }

    private static boolean noBottomInputs(Node n) {
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof FldOffNode) && def._type==Type.BOTTOM )
                return false;
        return true;
    }
}
