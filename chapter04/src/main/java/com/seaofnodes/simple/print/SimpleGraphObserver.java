package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphCapture;
import com.seaofnodes.graph.GraphViewer;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;
import java.io.IOException;

/** Chapter 4 needs only a parser; it has no worklist or distant dependencies. */
public class SimpleGraphObserver extends GraphCapture<Node> {
    private Parser _parser;
    private Node _ret;
    public SimpleGraphObserver() { super(new SimpleGraphAdapter()); }
    public static void main(String[] args) throws IOException {
        GraphViewer.run(new SimpleGraphObserver());
    }
    @Override protected void compile(String src) {
        _parser = new Parser(src);
        _parser._obs = this;
        _ret = _parser.parse();
        phase("Parse");
    }
    @Override protected String phase() { return "Parse"; }
    @Override protected int pos() { return _parser.pos(); }
    @Override protected void detach() {
        if( _parser != null ) _parser._obs = null;
        _parser = null;
        _ret = null;
    }
    @Override protected void roots(ArrayList<Node> roots) {
        roots.add(Parser.START);
        roots.add(_parser._scope);
        roots.add(_ret);
    }
}
