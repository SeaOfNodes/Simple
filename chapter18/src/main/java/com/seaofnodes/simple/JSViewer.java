package com.seaofnodes.simple;

import java.nio.file.Paths;

public class JSViewer {
    public static void main( String[] args ) throws Exception {
        // Launch server
        try( SimpleWebSocket server = new SimpleWebSocket(Paths.get("docs/index.html").toUri(),12345) ) {
            int N=0;
            while( true ) {
                String src = server.get();
                if( src==null || src.equals("null") )
                    break;
                System.out.println(src);
                // Client requests another frame
                if( src.charAt(0)=='+' ) {
                    if( N<2 ) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("digraph jsviewer_1 {\n");
                        sb.append("\tnode [shape=circle, style=filled];\n");
                        sb.append("\tSRC [label=\"").append("next frame").append("\", shape=oval];\n");
                        sb.append("}");
                        server.put(sb.toString());
                        System.out.println("Sent graph "+N);
                        N++;
                    } else {
                        // no more frames, send nothing
                    }
                } else {

                    StringBuilder sb = new StringBuilder();
                    sb.append("digraph jsviewer_0 {\n");
                    sb.append("\tnode [shape=circle, style=filled];\n");
                    sb.append("\tSRC [label=\"").append(src).append("\", shape=oval];\n");
                    sb.append("}");
                    server.put(sb.toString());
                    System.out.println("Sent graph "+N);
                    N++;
                }

                //// Client reset, send new program
                //server.put("!");
            }
            System.out.println("Client requested shutdown");
            server.close();
        }

    }

}
