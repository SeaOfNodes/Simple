package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphJson;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

public class JSViewer implements AutoCloseable {
    // Display programs in an endless loop
    public static void main( String[] args ) throws Exception {
        try(var js = new JSViewer()) {
            js.run();
        }
    }

    static boolean SHOW;

    // WebSocket to the browser for display
    static SimpleWebSocket SERVER;

    static long N;              // Snapshot step within this compilation
    static String COMP;
    static final SimpleGraphAdapter GRAPH = new SimpleGraphAdapter();

    JSViewer() throws Exception {
        // Launch server; handshake
        SERVER = new SimpleWebSocket(viewerURI(),12345);
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
        SERVER.put("!");
        while( true ) {
            String src = SERVER.get();
            switch( src ) {
            case null: return;
            case "null": return;
            case "+": break;    // Client requests more frames, but we send them all anyway
            default:
                System.out.println(src);
                try {
                    N=0;
                    COMP = UUID.randomUUID().toString();
                    // Use parser scope, xscope when building views
                    CodeGen code = new CodeGen(src);
                    SHOW = true;
                    show();
                    // Parse program, generating views at every parse point
                    code.parse();
                    // No longer user parse internal state when building views
                    code.opto();

                    // Catch and ignore Parser errors
                } catch(RuntimeException re) {
                    System.err.println(re);
                } finally {
                    SHOW = false;
                    SERVER.put("#"); // Final frame
                }
                break;
            }
        }
    }

    @Override public void close() throws IOException {
        SERVER.close();
        SERVER=null;
    }

    public static void show() { if( SERVER!=null && SHOW ) _show(); }
    private static void _show() {
        // Skip util we at least get the Parse object made
        if( CODE._phase==null || CODE._phase.ordinal() < CodeGen.Phase.Parse.ordinal() )
            return;
        boolean midParse = CODE._phase == CodeGen.Phase.Parse;
        var xScopes = midParse ? CODE.P._xScopes : null;
        var roots = new ArrayList<Node>();
        roots.add(CODE._stop);
        if( midParse ) {
            roots.add(CODE.P._scope);
            roots.addAll(xScopes);
        }
        long step = N++;
        var snap = GRAPH.snap(COMP, step, roots.toArray(Node[]::new));
        int pos = midParse ? CODE.P.pos() : -1;

        try {
            SERVER.put(GraphJson.frame(snap, pos));
        } catch( IOException ioe ) {
            try { SERVER.close(); } catch( IOException ignored ) {}
        }
    }
}