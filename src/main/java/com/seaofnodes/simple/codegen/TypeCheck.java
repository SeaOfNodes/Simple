package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.Ary;

// Reject invalid optimized programs before lowering erases their source-level
// obligations. Queue surviving CLInits during the same walk for Lower's Iter.
abstract class TypeCheck {
    static void run(CodeGen code) {
        // Check for class-init cycles first; such a cycle can prevent other
        // things from resolving types. Use the graph and summaries left by
        // optimization; do not load more bodies to disprove a possible cycle.
        ClassInitDependencies deps = new ClassInitDependencies(code);
        String cycle = deps.check();
        if( cycle!=null ) throw Parser.error(cycle,null);
        code._classInitDependencies = deps; // Serialize reuses this checked graph.

        Ary<Node> errs = new Ary<>(Node.class);
        code._stop.walk( n -> {
                if( n.err() != null )
                    errs.add(n);
                if( n instanceof CLInitNode ) code.add(n);
                return null;
            });

        if( !errs.isEmpty() ) {
            // Prefer an originating error over a secondary error caused by a
            // BOTTOM input.  If all errors are downstream-poisoned, retain
            // the old first-error behavior.
            Node min = null;
            for( Node n : errs )
                if( noBottomInputs(n) )
                    {  min = n;  break; }
            if( min==null )
                min = errs.at(0);
            for( Node n : errs )
                if( n!=min &&
                    noBottomInputs(n) &&
                    ((n   instanceof MemOpNode nmop &&
                      min instanceof MemOpNode mmop &&
                      nmop._loc.before(mmop._loc)) ||
                     (n   instanceof CFGNode ncfg &&
                      min instanceof CFGNode mincfg &&
                      ncfg.idepth() < mincfg.idepth())) )
                    min = n;
            if( code._obs != null ) code._obs.error(min, min.err().getMessage());
            throw min.err();
        }
    }

    private static boolean noBottomInputs(Node n) {
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof FldOffNode) && def._type==Type.BOTTOM )
                return false;
        return true;
    }
}
