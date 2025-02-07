package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphCapture;
import com.seaofnodes.graph.GraphViewer;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;
import java.io.IOException;

/** Chapter 20's compilation context; capture and event bookkeeping are shared. */
public class SimpleGraphObserver extends GraphCapture<Node> {
    private CodeGen _code;
    public SimpleGraphObserver() { super(new SimpleGraphAdapter()); }
    public static void main(String[] args) throws IOException {
        GraphViewer.run(new SimpleGraphObserver());
    }
    @Override protected void compile(String src) {
        _code = new CodeGen(src);
        _code._obs = this;
        _code.parse().opto().typeCheck();
    }
    @Override protected String phase() { return _code._phase.name(); }
    @Override protected int pos() { return _code._phase == CodeGen.Phase.Parse ? _code.P.pos() : -1; }
    @Override protected void detach() {
        if( _code != null ) _code._obs = null;
        _code = null;
    }
    @Override protected Node scope() { return _code.P._scope; }
    @Override protected void roots(ArrayList<Node> roots) {
        roots.add(_code._stop);
        if( _code._phase == CodeGen.Phase.Parse ) {
            roots.addAll(_code.P._xScopes);
        }
    }
}
