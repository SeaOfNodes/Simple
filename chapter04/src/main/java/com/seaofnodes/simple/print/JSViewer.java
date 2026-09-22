package com.seaofnodes.simple.print;

import com.seaofnodes.graph.*;
import com.seaofnodes.simple.Parser;
import java.io.IOException;

public class JSViewer extends GraphViewer {
    public static void main(String[] args) throws IOException {
        try( var view = new JSViewer() ) { view.run(); }
    }
    JSViewer() throws IOException {}

    @Override protected void compile(String src) {
        Parser parser = new Parser(src);
        parser._obs = new SimpleGraphObserver(parser) {
            @Override protected void frame(GraphSnapshot snap, int pos, GraphEvent evt) throws IOException {
                JSViewer.this.frame(snap, pos, evt);
            }
        };
        try {
            parser.parse();
        } finally {
            parser._obs = null;
        }
    }
}
