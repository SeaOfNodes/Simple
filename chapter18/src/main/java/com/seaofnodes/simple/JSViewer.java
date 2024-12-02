package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.nio.file.Paths;
import java.io.IOException;
import java.util.Stack;
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



                    // Catch and ignore Parser errors
                } catch(RuntimeException re) {
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
            String lab = n.glabel();
            if( n instanceof MultiNode ) {
                // Make a box with the MultiNode on top, and all the projections on the bottom
                sb.    p("shape=plaintext label=<\n").ii();
                sb.i().p("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\">\n");
                // Row over single cell for node label
                cell(sb.i().p("<TR>"), lab, null, true, false).p("</TR>\n");
                // Row over single cell, for nested table
                sb.i().p("<TR><TD>\n").ii();
                sb.i().p("<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">").p("\n");
                sb.i().p("<TR>");
                n._outputs.sort((x,y) -> x instanceof ProjNode xp && y instanceof ProjNode yp ? (xp._idx - yp._idx) : ((x==null ? 99999 : x._nid) - (y==null ? 99999 : y._nid)));
                boolean empty_row=true;
                for( Node use : n._outputs )
                    if( use instanceof MultiUse muse ) {
                        cell(sb,use.glabel(),"p"+muse.idx(),use instanceof CFGNode,use.isMem());
                        empty_row=false;
                    }
                // At least one cell on row
                if( empty_row )
                    cell(sb,"",null,false,false);
                sb.    p("</TR>").p("\n");
                sb.i().p("</TABLE>").p("\n").di();
                sb.i().p("</TD></TR>\n");
                sb.i().p("</TABLE>>\n").di();
                sb.i().p("];\n");

            } else {
                // control nodes have box shape
                // other nodes are ellipses, i.e. default shape
                if( n instanceof CFGNode ) sb.p("shape=box style=filled fillcolor=yellow ");
                else if (n instanceof PhiNode) sb.p("style=filled fillcolor=lightyellow ");
                sb.p("label=\"").p(lab).p("\" ];\n");
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
            // Add the scope level
            sb.i().p("<TR><TD BGCOLOR=\"cyan\">").p(level).p("</TD>");
            int lexStart=scope._lexSize.at(level);
            // Special for memory ScopeMinNode
            ScopeMinNode n = scope.nIns()>1 ? scope.mem() : null;
            if( level==0 && n!=null && n.nIns()>2 ) {
                sb.    p("<TD BGCOLOR=\"blue\" BORDER=0 colspan=\"").p(last-lexStart-1).p("\">\n");
                sb.i().p("\t<TABLE><TR>");
                for( int m=2; m<n.nIns(); m++ ) {
                    sb.p("<TD>").p(m).p("</TD>");
                    throw Utils.TODO(); // Need port names for edges
                }
                sb.p("</TR></TABLE>\n");
                sb.i().p("</TD>");
            }
            sb.    p("</TR>\n"); // End scope level
            sb.i().p("<TR>\n");
            for( int j=lexStart; j<last; j++ ) {
                var v = scope._vars.at(j);
                cell(sb.i(),v._name,makePortName(scopeName, v._name),j==0,j==1).nl();
            }
            last = lexStart;
            sb.i().p("</TR>\n");
            sb.i().p("</TABLE>>];\n").di();
        }
        // Scope clusters nest, so the graphics shows the nested scopes, so
        // they are not closed as they are printed; so they just keep nesting.
        // We close them all at once here.
        for( int i=0; i<max; i++ )
            sb.di().i().p("}\n");
    }

    // Append a cell, with color
    private static SB cell(SB sb, String text, String port, boolean isCFG, boolean isMem) {
        sb.p("<TD ");
        if( port!=null )  sb.p("PORT=\"").p(port).p("\"");
        if( isCFG ) sb.p(" BGCOLOR=\"yellow\"");
        if( isMem ) sb.p(" BGCOLOR=\"blue\"");
        sb.p(">");
        if( isMem ) sb.p("<FONT color=\"white\">");
        sb.p(text);
        if( isMem ) sb.p("</FONT>");
        sb.p("</TD>");
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
                    // The edge from New to ctrl is just for anchoring the New
                    if ( n instanceof NewNode )
                        sb.p(" color=green");
                    // control edges are colored red
                    else if( def instanceof CFGNode )
                        sb.p(" color=red");
                    else if( def.isMem() )
                        sb.p(" color=blue");
                    // Backedges do not add a ranking constraint
                    if( i==2 && (n instanceof PhiNode || n instanceof LoopNode) )
                        sb.p(" constraint=false");
                    sb.p("];\n");
                }
            }

            // Bonus edge if hooked by parser
            if( (n.iskeep() || n.isUnused()) && scopeName != null ) {
                sb.i().p(scopeName).p(" -> ").p(n.uniqueName()).p(" [ stype=dotted color=grey]\n");
            }
        }
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
            if( def instanceof CProjNode proj ) {
                sb.p(def.in(0).uniqueName()).p(":p").p(proj._idx);
            } else if( def instanceof ProjNode proj ) {
                sb.p(def.in(0).uniqueName()).p(":p").p(proj._idx);
            } else sb.p(def.uniqueName());
            sb.p(";\n");
        }
    }


}
