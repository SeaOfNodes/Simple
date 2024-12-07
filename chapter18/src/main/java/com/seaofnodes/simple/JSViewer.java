package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.nio.file.Paths;
import java.io.IOException;
import java.util.Collection;

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

    static int N;               // Dot frames
    static Parser P;            //
    static StopNode STOP;       //

    JSViewer() throws Exception {
        // Launch server; hand shake
        SERVER = new SimpleWebSocket(Paths.get("docs/index.html").toUri(),12345) ;
    }

    void run( ) throws Exception {
        SERVER.put("!");
        while( true ) {
            String src = SERVER.get();
            switch( src ) {
            case null: return;
            case "null": return;
            case "+": break;    // Client requests more frames, but we send them all anyways
            default:
                System.out.println(src);
                try {
                    N=0;
                    // Use parser scope, xscope when building views
                    P = new Parser(src);
                    STOP = P.STOP;
                    SHOW = true;
                    show();
                    // Parse program, generating views at every parse point
                    P.parse();
                    // No longer user parse internal state when building views
                    P = null;
                    STOP.iterate();


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
        Collection<Node> all = GraphVisualizer.findAll(P==null ? null : P._xScopes, STOP, P==null ? null : P._scope);
        SB sb = new SB();
        sb.p("digraph view_").p(N++).p(" {\n").ii();
        if( P!=null )
            sb.i().p("// POS: ").p(P.pos()).nl();

        // To keep the Scopes below the graph and pointing up into the graph we
        // need to group the Nodes in a subgraph cluster, and the scopes into a
        // different subgraph cluster.  THEN we can draw edges between the
        // scopes and nodes.  If we try to cross subgraph cluster borders while
        // still making the subgraphs DOT gets confused.
        sb.i().p("rankdir=BT;\n"); // Force Nodes before Scopes

        // CNC - turned off Apr/8/2024, gives more flex in the layout and
        // removes some of the more ludicrous layout choices.
        // Preserve node input order
        //sb.append("ordering=\"in\";\n");

        // Merge multiple edges hitting the same node.  Makes common shared
        // nodes much prettier to look at.
        sb.i().p("concentrate=\"true\";\n");

        // Force nested scopes to order
        sb.i().p("compound=\"true\";\n");

        // Just the Nodes first, in a cluster no edges
        nodes(sb, all);

        // Now the scopes, in a cluster no edges
        if( P!=null )
            for( ScopeNode xscope : P._xScopes )
                if( !xscope.isDead() )
                    scope( sb, xscope );

        // Walk the Node edges
        nodeEdges(sb, P!=null && !P._xScopes.empty() ? P._xScopes.peek() : null, all);

        // Walk the active Scope edges
        if( P!=null )
            for( ScopeNode xscope : P._xScopes )
                if( !xscope.isDead() )
                    scopeEdges( sb, xscope );


        sb.p("}\n").di();
        // Tell client another DOT frame
        String dot = sb.toString();
        System.out.println(dot);
        try {
            SERVER.put(dot);
        } catch( IOException ioe ) {
            try { SERVER.close();} catch( IOException ignored ){}
        }
    }


    private static void nodes(SB sb, Collection<Node> all) {
        // Just the Nodes first, in a cluster no edges
        sb.i().p("subgraph cluster_Nodes {\n").ii(); // Magic "cluster_" in the subgraph name
        for (Node n : all) {
            if( n instanceof ProjNode || n instanceof CProjNode || n instanceof ScopeMinNode || n==Parser.XCTRL )
                continue; // Do not emit, rolled into MultiNode or Scope cluster already
            sb.i().p(n.uniqueName()).p(" [ ");
            if( n instanceof MultiNode ) {
                // Make a box with the MultiNode on top, and all the projections on the bottom
                sb.    p("shape=plaintext label=<\n").ii();
                sb.i().p("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\">\n");
                // Row over single cell for node label
                cell(sb.i().p("<TR>"), n.glabel(), n, null).p("</TR>\n");
                // Row over single cell, for nested table
                sb.i().p("<TR><TD>\n").ii();
                sb.i().p("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">").p("\n");
                sb.i().p("<TR>");
                n._outputs.sort((x,y) -> x instanceof ProjNode xp && y instanceof ProjNode yp ? (xp._idx - yp._idx) : ((x==null ? 99999 : x._nid) - (y==null ? 99999 : y._nid)));
                boolean empty_row=true;
                for( Node use : n._outputs )
                    if( use instanceof MultiUse muse ) {
                        cell(sb,use.glabel(),use,"p"+muse.idx());
                        empty_row=false;
                    }
                // At least one cell on row
                if( empty_row )  sb.p("<TD></TD>");
                sb.    p("</TR>").p("\n");
                sb.i().p("</TABLE>").p("\n").di();
                sb.i().p("</TD></TR>\n");
                sb.i().p("</TABLE>>\n").di();
                sb.i().p("];\n");

            } else {
                // control nodes have box shape
                // other nodes are ellipses, i.e. default shape
                //else if (n instanceof PhiNode) sb.p("style=filled fillcolor=lightyellow ");
                node(sb,n.glabel(),n);
                sb.p("];\n");
            }
        }
        // Force Region & Phis to line up
        for (Node n : all) {
            if (n instanceof RegionNode region) {
                sb.i().p("{ rank=same; ");
                sb.p(region.uniqueName()).p(";");
                for (Node phi : region._outputs)
                    if (phi instanceof PhiNode) sb.p(phi.uniqueName()).p(";");
                sb.p("}\n");
            }
        }
        sb.di().i().p("}").nl();     // End Node cluster
    }

    // Build a nested scope display, walking the _prev edge
    private static void scope( SB sb, ScopeNode scope ) {
        sb.i().p("node [shape=plaintext];\n");
        int last = scope.nIns();
        int max = scope._lexSize.size();
        for( int i = 0; i < max; i++ ) {
            int level = max-i-1;
            String scopeName = makeScopeName(scope, level);
            sb.i().p("subgraph cluster_").p(scopeName).p(" {\n").ii(); // Magic "cluster_" in the subgraph name
            sb.i().p(scopeName).p(" [label=<\n").ii();
            sb.i().p("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">\n");
            int lexStart=scope._lexSize.at(level);
            // Special for memory ScopeMinNode
            ScopeMinNode n = scope.nIns()>1 ? scope.mem() : null;
            if( level==0 && n!=null && n.nIns()>2 ) {
                sb.i().p("<TR>");
                for( int m=2; m<n.nIns(); m++ )
                    cell(sb,"#"+m,n.in(m),"m"+m);
                sb.p("</TR>\n"); // End scope level
            }
            // Scope variables, empty if none
            if( lexStart<last ) {
                sb.i().p("<TR>\n");
                for( int j=lexStart; j<last; j++ ) {
                    var v = scope._vars.at(j);
                    cell(sb.i(),v._name,scope.in(j),makePortName(scopeName, v._name)).nl();
                }
                last = lexStart;
                sb.i().p("</TR>\n");
            }
            sb.i().p("</TABLE>>];\n").di();
            // Scope label
            sb.i().p(scopeName).p("A [label=\"").p(level).p("\" shape=doublecircle style=filled fillcolor=aqua fontsize=10 margin=0 width=0.2 ];\n");
        }
        // Scope clusters nest, so the graphics shows the nested scopes, so
        // they are not closed as they are printed; so they just keep nesting.
        // We close them all at once here.
        for( int i=0; i<max; i++ )
            sb.di().i().p("}\n");
    }

    // Append a cell, with color
    private static SB cell(SB sb, String text, Node n, String port) {
        boolean dark=false;
        sb.p("<TD");
        if( n instanceof CFGNode    ) sb.p(" BGCOLOR=\"yellow\"");
        if( n instanceof NewNode    ) sb.p(" BGCOLOR=\"lightgreen\"");
        if( n instanceof StructNode ) sb.p(" BGCOLOR=\"lightgreen\"");
        if( n.isMem() )             { sb.p(" BGCOLOR=\"blue\""); dark=true; }
        if( n._type instanceof TypeMemPtr ) { sb.p(" BGCOLOR=\"green\""); dark=true; }
        if( n._type instanceof TypeInteger )  sb.p(" BGCOLOR=\"lightblue\"");
        if( port!=null )  sb.p(" PORT=\"").p(port).p("\"");
        return colorcell(sb.p(">"),text,n,dark).p("</TD>");
    }
    private static SB node(SB sb, String text, Node n) {
        boolean dark=false;
        if( n instanceof CFGNode )   sb.p("style=filled fillcolor=yellow shape=box ");
        if( n instanceof PhiNode )   sb.p("style=filled fillcolor=lightyellow ");
        if( n instanceof StructNode) sb.p("style=filled fillcolor=lightgreen ");
        if( n.isMem()            ) { sb.p("style=filled fillcolor=blue "); dark=true; }
        if( n._type instanceof TypeMemPtr ) { sb.p("style=filled fillcolor=green "); dark=true; }
        if( n._type instanceof TypeInteger )  sb.p("style=filled fillcolor=lightblue ");
        return colorcell(sb.p("label=<"),text,n,dark).p(">");
    }
    // Called with an open div
    private static SB colorcell(SB sb, String text, Node n, boolean dark) {
        if( dark ) sb.p("<font color=\"white\">");
        sb.p(text);
        if( n._type!=null ) {
            sb.p("<br /><font point-size=\"10\">");
            n._type.gprint(sb);
            sb.p("</font>");
        }
        if( dark ) sb.p("</font>");
        return sb;
    }


    private static String makeScopeName(ScopeNode sn, int level) { return sn.uniqueName() + "_" + level; }
    private static String makePortName(String scopeName, String varName) { return scopeName + "_" + varName; }

    // Walk the node edges
    private static void nodeEdges(SB sb, ScopeNode scope, Collection<Node> all) {
        // All them edge labels
        String scopeName = scope!=null ? makeScopeName(scope, 0) : null;
        sb.i().p("edge [ fontname=Helvetica, fontsize=8 ];\n");
        for( Node n : all ) {
            // Do not display the Constant->Start edge;
            if( n instanceof ConstantNode ||
                n instanceof XCtrlNode ||
                // ProjNodes handled by Multi;
                n instanceof ProjNode ||
                n instanceof CProjNode ||
                // ScopeNodes are done separately
                n instanceof ScopeNode ||
                n instanceof ScopeMinNode
                )
                continue;
            if( n.isDead() )
                throw Utils.TODO();
            // Normal edges
            for( int i=0; i<n.nIns(); i++ ) {
                Node def = n.in(i);
                if( def==null ) continue;
                sb.i().p(n.uniqueName()).p(" -> ");
                if( n instanceof PhiNode && def instanceof RegionNode ) {
                    // Draw a dotted use->def edge from Phi to Region.
                    sb.p(def.uniqueName());
                    sb.p(" [style=dotted taillabel=").p(i).p("];\n");
                } else {
                    // Most edges land here use->def
                    if( def instanceof CProjNode proj ) {
                        String mname = proj.ctrl().uniqueName();
                        sb.p(mname).p(":p").p(proj._idx);
                    } else if( def instanceof ProjNode proj ) {
                        String mname = proj.in(0).uniqueName();
                        sb.p(mname).p(":p").p(proj._idx);
                    } else sb.p(def.uniqueName());
                    // Number edges, so we can see how they track
                    sb.p("[taillabel=").p(i);
                    // Color the edge
                    nodeEdgeColor(sb,def._type);
                    // Backedges do not add a ranking constraint
                    if( i==2 && (n instanceof PhiNode || n instanceof LoopNode) )
                        sb.p(" constraint=false");
                    sb.p("];\n");
                }
            }

            // Bonus edge if hooked by parser
            if( (n.iskeep() || n.isUnused()) && scopeName != null ) {
                sb.i().p(scopeName).p(" -> ").p(n.uniqueName()).p(" [ style=dashed color=grey];\n");
            }
        }
    }

    private static void nodeEdgeColor(SB sb, Type t) {
        String color = switch( t ) {
        case TypeMem mem -> "blue";
        case TypeMemPtr ptr -> "green";
        case TypeInteger ti -> "lightblue";
        // control edges are colored red
        default -> (t==Type.CONTROL || t==Type.XCONTROL) ? "red" : null;
        };
        if( color!=null )
            sb.p(" color=").p(color);
    }

    // Walk the scope edges
    private static void scopeEdges( SB sb, ScopeNode scope ) {
        sb.i().p("edge [style=dashed color=cornflowerblue];\n");
        int level=0;
        for( var v : scope._vars ) {
            if( v._idx==1 ) continue; // Memory done seperately
            Node def = scope.in(v._idx);
            while( def instanceof ScopeNode lazy )
                def = lazy.in(v._idx);
            if( def==null ) continue;
            while( level < scope._lexSize.size() && v._idx >= scope._lexSize.at(level) )
                level++;
            String scopeName = makeScopeName(scope, level-1);
            sb.i()
                .p(scopeName).p(":")
                .p('"').p(makePortName(scopeName, v._name)).p('"') // wrap port name with quotes because $ctrl is not valid unquoted
                .p(" -> ");
            defPort(sb,def).p(";\n");
        }

        // Memory
        if( scope.nIns()>1 ) {
            ScopeMinNode n = scope.mem();
            for( int i=2; i<n.nIns(); i++ ) {
                Node def = n.in(i);
                while( def instanceof ScopeNode lazy )
                    def = lazy.in(i);
                if( def==null ) continue;
                String scopeName = makeScopeName(scope, 0);
                sb.i().p(scopeName).p(":m").p(i).p(" -> ");
                defPort(sb,def).p(";\n");
            }
        }

    }

    private static SB defPort(SB sb, Node def) {
        return def instanceof MultiUse muse
            ? sb.p(def.in(0).uniqueName()).p(":p").p(muse.idx())
            : sb.p(def.uniqueName());
    }
}