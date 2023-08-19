package com.seaofnodes.graph;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared launcher and source/compile loop. Chapter observers supply their compiler. */
public final class GraphViewer {
    private GraphViewer() {}

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

    public static void run(GraphCapture<?> obs) throws IOException {
        try( var server = new GraphSocket(viewerURI(), 12345) ) {
            server.put("!");
            while( true ) {
                String src = server.get();
                if( src == null || src.equals("null") ) return;
                if( src.equals("+") ) continue; // Frames are currently sent eagerly.
                System.out.println(src);
                try {
                    obs.run(src, server);
                } catch( RuntimeException e ) {
                    System.err.println(e);
                    server.put(GraphJson.error(e.toString()));
                } finally {
                    server.put("#");
                }
            }
        }
    }
}
