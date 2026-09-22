package com.seaofnodes.graph;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared launcher and source/compile loop. Subclasses supply their compiler. */
public abstract class GraphViewer implements AutoCloseable {
    private final GraphSocket _server;

    protected GraphViewer() throws IOException { _server = new GraphSocket(viewerURI(), 12345); }
    protected abstract void compile(String src);

    protected final void frame(GraphSnapshot snap, int pos, GraphEvent evt) throws IOException {
        _server.put(GraphJson.frame(snap, pos, evt));
    }

    private static URI viewerURI() throws IOException {
        String url = System.getProperty("simple.graph.url");
        if( url != null ) return URI.create(url);
        for( Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent() ) {
            Path page = dir.resolve("graph/web/index.html");
            if( Files.isRegularFile(page) ) return page.toUri();
        }
        throw new IOException("Cannot find graph/web/index.html; run inside the Simple checkout "
                              + "or set -Dsimple.graph.url=<viewer URL>");
    }

    public final void run() throws IOException {
        _server.put("!");
        while( true ) {
            String src = _server.get();
            if( src == null || src.equals("null") ) return;
            if( src.equals("+") ) continue; // Frames are currently sent eagerly.
            System.out.println(src);
            try {
                compile(src);
            } catch( RuntimeException e ) {
                System.err.println(e);
                _server.put(GraphJson.error(e.toString()));
            } finally {
                _server.put("#");
            }
        }
    }

    @Override public final void close() throws IOException { _server.close(); }
}
