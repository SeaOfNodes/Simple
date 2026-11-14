package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeScalar;
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
        Ary<ConstructNode> constructs = new Ary<>(ConstructNode.class);
        Ary<InitViewNode> views = new Ary<>(InitViewNode.class);
        Ary<ArrayInitNode> arrays = new Ary<>(ArrayInitNode.class);
        code._stop.walk( n -> {
                if( n.err() != null )
                    errs.add(n);
                if( n instanceof CLInitNode ) code.add(n);
                if( n instanceof ConstructNode con ) constructs.add(con);
                if( n instanceof InitViewNode view ) views.add(view);
                if( n instanceof ArrayInitNode array ) arrays.add(array);
                return null;
            });

        if( !errs.isEmpty() ) {
            // Prefer an originating error over a secondary error caused by a
            // BOTTOM input.  If all errors are downstream-poisoned, retain
            // the old first-error behavior.
            Node min = errs.at(0);
            for( Node n : errs )
                if( n!=min &&
                    (errorRank(n)<errorRank(min) || (errorRank(n)==errorRank(min) &&
                    ((n   instanceof MemOpNode nmop &&
                      min instanceof MemOpNode mmop &&
                      nmop._loc.before(mmop._loc)) ||
                     (n instanceof ConstructNode nc && min instanceof ConstructNode mc &&
                      nc._loc!=null && nc._loc.before(mc._loc)) ||
                     (n   instanceof CFGNode ncfg &&
                      min instanceof CFGNode mincfg &&
                      ncfg.idepth() < mincfg.idepth())))) )
                    min = n;
            if( code._obs != null ) code._obs.error(min, min.err().getMessage());
            throw min.err();
        }
        // Checked construction boundaries and views have no runtime behavior. Gathered
        // above during the existing error walk; no separate graph pass/solve.

        for( ConstructNode con : constructs )
            if( !con.isDead() ) con.subsume(con.in(1));
        for( InitViewNode view : views )
            if( !view.isDead() ) view.subsume(view.in(1));
        for( ArrayInitNode array : arrays )
            if( !array.isDead() ) array.subsume(array.in(2));
    }

    private static int errorRank(Node n) {
        // Bad memory access originates the error. Missing construction fields
        // in turn explain generic-scalar arithmetic further downstream.
        if( n instanceof MemOpNode && noBottomInputs(n) ) return 0;
        if( n instanceof ConstructNode || n instanceof ArrayInitNode ) return 1;
        return noBottomInputs(n) ? 2 : 3;
    }

    private static boolean noBottomInputs(Node n) {
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof FldOffNode) && (def._type==Type.BOTTOM || def._type==TypeScalar.BOT) )
                return false;
        return true;
    }
}
