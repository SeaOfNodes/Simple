package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphJson;
import com.seaofnodes.graph.GraphEvent;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;

public class JSViewer implements AutoCloseable {
    // Display programs in an endless loop
    public static void main( String[] args ) throws Exception {
        try(var js = new JSViewer()) {
            js.run();
        }
    }

    // WebSocket to the browser for display
    private final SimpleWebSocket _server;

    JSViewer() throws Exception {
        // Launch server; handshake
        _server = new SimpleWebSocket(viewerURI(),12345);
    }

    // The browser application is shared by all chapters. Search upwards so
    // chapter, repository-root, and linearized checkouts use the same assets.
    static URI viewerURI() throws IOException {
        String url = System.getProperty("simple.graph.url");
        if( url != null ) return URI.create(url);
        for( Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent() ) {
            Path page = dir.resolve("graph/web/index.html");
            if( Files.isRegularFile(page) ) return page.toUri();
        }
        throw new IOException("Cannot find graph/web/index.html; run inside the Simple checkout "
                              + "or set -Dsimple.graph.url=<viewer URL>");
    }

    void run( ) throws Exception {
        _server.put("!");
        while( true ) {
            String src = _server.get();
            switch( src ) {
            case null: return;
            case "null": return;
            case "+": break;    // Client requests more frames, but we send them all anyway
            default:
                System.out.println(src);
                try {
                    compile(src);
                    // Catch and ignore Parser errors
                } catch(RuntimeException re) {
                    System.err.println(re);
                } finally {
                    _server.put("#"); // Final frame
                }
                break;
            }
        }
    }

    private void compile(String src) {
        CodeGen code = new CodeGen(src);
        code._obs = new SimpleGraphObserver(code) {
            @Override protected void frame(GraphSnapshot snap, int pos, GraphEvent evt) throws IOException {
                _server.put(GraphJson.frame(snap, pos, evt));
            }
        };
        try {
            code.parse().opto();
        } finally {
            code._obs = null;
        }
    }

    @Override public void close() throws IOException { _server.close(); }
}
