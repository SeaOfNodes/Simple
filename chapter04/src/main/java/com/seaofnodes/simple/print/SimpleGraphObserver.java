package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphCapture;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;

/** Chapter 4 needs only a parser; it has no worklist or distant dependencies. */
public abstract class SimpleGraphObserver extends GraphCapture<Node> {
    private final Parser _parser;
    protected SimpleGraphObserver(Parser parser) { super(new SimpleGraphAdapter()); _parser = parser; }
    @Override protected String phase() { return "Parse"; }
    @Override protected int pos() { return _parser.pos(); }
    @Override protected void detach() { _parser._obs = null; }
    @Override protected void roots(ArrayList<Node> roots) {
        roots.add(Parser.START);
        roots.add(_parser._scope);
        roots.add(_parser._ret);
    }
}
