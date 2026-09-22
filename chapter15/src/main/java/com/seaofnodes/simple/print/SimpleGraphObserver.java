package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphCapture;
import com.seaofnodes.graph.GraphViewer;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;
import java.io.IOException;

/** Chapter 15's compilation context; capture and event bookkeeping are shared. */
public class SimpleGraphObserver extends GraphCapture<Node> {
    private Parser _parser;
    private Node _ret;
    private String _phase;
    public SimpleGraphObserver() { super(new SimpleGraphAdapter()); }
    public static void main(String[] args) throws IOException {
        GraphViewer.run(new SimpleGraphObserver());
    }
    @Override protected void compile(String src) {
        _parser = new Parser(src);
        _parser._obs = this;
        _phase = "Parse";
        _ret = _parser.parse();
        phase(_phase);
        _phase = "Iter";
        com.seaofnodes.simple.IterPeeps.iterate(_parser.STOP);
        phase(_phase);
    }
    @Override protected String phase() { return _phase; }
    @Override protected int pos() { return "Parse".equals(_phase) ? _parser.pos() : -1; }
    @Override protected void detach() {
        if( _parser != null ) _parser._obs = null;
        _parser = null;
        _ret = null;
    }
    @Override protected Node scope() { return _parser._scope; }
    @Override protected void roots(ArrayList<Node> roots) {
        roots.add(Parser.START);
        roots.add(_parser.STOP);
        roots.addAll(_parser._xScopes);
        roots.add(_ret);
    }
}
