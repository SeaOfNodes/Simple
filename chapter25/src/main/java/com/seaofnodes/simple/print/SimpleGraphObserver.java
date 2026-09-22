package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphCapture;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;

/** Chapter 25's compilation context; capture and event bookkeeping are shared. */
public abstract class SimpleGraphObserver extends GraphCapture<Node> {
    private final CodeGen _code;
    protected SimpleGraphObserver(CodeGen code) { super(new SimpleGraphAdapter()); _code = code; }
    @Override protected String phase() { return _code._phase.name(); }
    @Override protected int pos() { return _code._phase == CodeGen.Phase.Parse ? _code.P.pos() : -1; }
    @Override protected void detach() { _code._obs = null; }
    @Override protected void roots(ArrayList<Node> roots) {
        roots.add(_code._stop);
        if( _code._phase == CodeGen.Phase.Parse ) {
            roots.add(_code.P._scope);
            roots.addAll(_code.P._xScopes);
        }
    }
}
