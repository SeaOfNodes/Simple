package com.seaofnodes.graph;

/** JSON for the shared graph model; no chapter or layout dependencies. */
public class GraphJson {
    public static String error(String msg) {
        var sb = new StringBuilder("{\"error\":");
        str(sb, msg);
        return sb.append('}').toString();
    }

    public static String write(GraphSnapshot snap) {
        var sb = new StringBuilder();
        snap(sb, snap);
        return sb.toString();
    }

    public static String frame(GraphSnapshot snap, int pos, GraphEvent evt) {
        var sb = new StringBuilder("{\"snap\":");
        snap(sb, snap);
        sb.append(",\"pos\":").append(pos).append(",\"evt\":{\"kind\":");
        str(sb, evt.kind().name());
        sb.append(",\"peep\":").append(evt.peep()).append(",\"up\":").append(evt.up())
          .append(",\"phase\":");
        str(sb, evt.phase());
        sb.append(",\"node\":").append(evt.node()).append(",\"repl\":").append(evt.repl())
          .append(",\"near\":[");
        int[] near = evt.near();
        for( int i = 0; i < near.length; i++ ) {
            if( i > 0 ) sb.append(',');
            sb.append(near[i]);
        }
        sb.append("],\"temps\":[");
        int[] temps = evt.temps();
        for( int i = 0; i < temps.length; i++ ) {
            if( i > 0 ) sb.append(',');
            sb.append(temps[i]);
        }
        sb.append(']');
        if( evt.msg() != null ) {
            sb.append(",\"msg\":");
            str(sb, evt.msg());
        }
        sb.append('}');
        return sb.append('}').toString();
    }

    private static void snap(StringBuilder sb, GraphSnapshot snap) {
        sb.append("{\"ver\":").append(snap.ver()).append(",\"comp\":");
        str(sb, snap.comp());
        sb.append(",\"step\":").append(snap.step()).append(",\"roots\":[");
        int[] roots = snap.roots();
        for( int i = 0; i < roots.length; i++ ) {
            if( i > 0 ) sb.append(',');
            sb.append(roots[i]);
        }
        sb.append("],\"scope\":").append(snap.scope()).append(",\"nodes\":[");
        var nodes = snap.nodes();
        for( int i = 0; i < nodes.size(); i++ ) {
            if( i > 0 ) sb.append(',');
            var node = nodes.get(i);
            sb.append("{\"id\":").append(node.id()).append(",\"label\":");
            str(sb, node.label());
            sb.append(",\"type\":");
            str(sb, node.type());
            sb.append(",\"kind\":");
            str(sb, node.kind().name());
            if( node.folding() ) sb.append(",\"folding\":true");
            sb.append(",\"edges\":[");
            var edges = node.edges();
            for( int j = 0; j < edges.size(); j++ ) {
                if( j > 0 ) sb.append(',');
                var edge = edges.get(j);
                sb.append("{\"idx\":").append(edge.idx())
                  .append(",\"def\":").append(edge.def()).append(",\"role\":");
                str(sb, edge.role().name());
                sb.append(",\"label\":");
                str(sb, edge.label());
                if( edge.jump()!=0 ) sb.append(",\"jump\":").append(edge.jump());
                sb.append('}');
            }
            sb.append("],\"proj\":");
            var proj = node.proj();
            if( proj == null ) sb.append("null");
            else sb.append("{\"par\":").append(proj.par())
                   .append(",\"idx\":").append(proj.idx()).append('}');
            sb.append('}');
        }
        sb.append("],\"groups\":[");
        for( int i=0; i<snap.groups().size(); i++ ) {
            if( i>0 ) sb.append(',');
            var g = snap.groups().get(i);
            sb.append("{\"id\":").append(g.id()).append(",\"par\":").append(g.par()).append(",\"nodes\":[");
            for( int j=0; j<g.nodes().length; j++ ) {
                if( j>0 ) sb.append(',');
                sb.append(g.nodes()[j]);
            }
            sb.append("]}");
        }
        sb.append("]}");
    }

    private static void str(StringBuilder sb, String s) {
        if( s == null ) { sb.append("null"); return; }
        sb.append('"');
        for( int i = 0; i < s.length(); i++ ) {
            char c = s.charAt(i);
            switch( c ) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            default -> {
                // Escape controls and UTF-16 code units that cannot travel alone
                // in UTF-8. Surrogate pairs round-trip through JSON as well.
                if( c < 32 || Character.isSurrogate(c) ) {
                    sb.append("\\u");
                    for( int shift = 12; shift >= 0; shift -= 4 )
                        sb.append("0123456789abcdef".charAt((c >> shift) & 15));
                } else sb.append(c);
            }
            }
        }
        sb.append('"');
    }
}
