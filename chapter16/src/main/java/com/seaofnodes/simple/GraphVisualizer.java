package com.seaofnodes.simple;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;

import java.util.*;

import static com.seaofnodes.simple.Utils.TODO;

/**
 * Simple visualizer that outputs GraphViz dot format.
 * The dot output must be saved to a file and run manually via dot to generate the SVG output.
 * Currently, this is done manually.
 */
public class GraphVisualizer {

    /**
     * If set to true we put the control nodes in a separate cluster from
     * data nodes.
     */
    boolean _separateControlCluster = false;

    public GraphVisualizer(boolean separateControlCluster) { this._separateControlCluster = separateControlCluster; }
    public GraphVisualizer() { this(false); }

    public String generateDotOutput(Parser parse) { return generateDotOutput(parse.STOP,parse._scope,parse._xScopes); }
    public String generateDotOutput(StopNode stop, Node scope, Stack<ScopeNode> xScopes) {

        // Since the graph has cycles, we need to create a flat list of all the
        // nodes in the graph.
        Collection<Node> all = findAll(stop, scope);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph chapter11 {\n");
        sb.append("/*\n");
        sb.append(stop._src);
        sb.append("\n*/\n");

        // To keep the Scopes below the graph and pointing up into the graph we
        // need to group the Nodes in a subgraph cluster, and the scopes into a
        // different subgraph cluster.  THEN we can draw edges between the
        // scopes and nodes.  If we try to cross subgraph cluster borders while
        // still making the subgraphs DOT gets confused.
        sb.append("\trankdir=BT;\n"); // Force Nodes before Scopes

        // CNC - turned off Apr/8/2024, gives more flex in the layout and
        // removes some of the more ludicrous layout choices.
        // Preserve node input order
        //sb.append("\tordering=\"in\";\n");

        // Merge multiple edges hitting the same node.  Makes common shared
        // nodes much prettier to look at.
        sb.append("\tconcentrate=\"true\";\n");

        // Force nested scopes to order
        sb.append("\tcompound=\"true\";\n");

        // Just the Nodes first, in a cluster no edges
        nodes(sb, all);

        // Now the scopes, in a cluster no edges
        if( xScopes != null )
            for( ScopeNode xscope : xScopes )
                if( !xscope.isDead() )
                    scope( sb, xscope );

        // Walk the Node edges
        nodeEdges(sb, all);

        // Walk the active Scope edges
        if( xScopes != null )
            for( ScopeNode xscope : xScopes )
                if( !xscope.isDead() )
                    scopeEdges( sb, xscope );

        sb.append("}\n");
        return sb.toString();
    }

    private void nodesByCluster(StringBuilder sb, boolean doCtrl, Collection<Node> all) {
        if (!_separateControlCluster && doCtrl) // all nodes in 1 cluster
            return;
        // Just the Nodes first, in a cluster no edges
        sb.append(doCtrl ? "\tsubgraph cluster_Controls {\n" : "\tsubgraph cluster_Nodes {\n"); // Magic "cluster_" in the subgraph name
        for (Node n : all) {
            if (n instanceof ProjNode || n instanceof CProjNode || n instanceof ScopeNode || n==Parser.XCTRL)
                continue; // Do not emit, rolled into MultiNode or Scope cluster already
            if( _separateControlCluster &&  doCtrl && !(n instanceof CFGNode) ) continue;
            if( _separateControlCluster && !doCtrl &&  (n instanceof CFGNode) ) continue;
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            String lab = n.glabel();
            if (n instanceof MultiNode) {
                // Make a box with the MultiNode on top, and all the projections on the bottom
                sb.append("shape=plaintext label=<\n");
                sb.append("\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\">\n");
                sb.append("\t\t\t<TR><TD BGCOLOR=\"yellow\">").append(lab).append("</TD></TR>\n");
                sb.append("\t\t\t<TR>");
                boolean doProjTable = false;
                n._outputs.sort((x,y) -> x instanceof ProjNode xp && y instanceof ProjNode yp ? (xp._idx - yp._idx) : (x._nid - y._nid));
                for (Node use : n._outputs) {
                    if (use instanceof ProjNode proj) {
                        if (!doProjTable) {
                            doProjTable = true;
                            sb.append("<TD>").append("\n");
                            sb.append("\t\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">").append("\n");
                            sb.append("\t\t\t\t<TR>");
                        }
                        sb.append("<TD PORT=\"p").append(proj._idx).append("\">").append(proj.glabel()).append("</TD>");
                    }
                    if (use instanceof CProjNode proj) {
                        if (!doProjTable) {
                            doProjTable = true;
                            sb.append("<TD>").append("\n");
                            sb.append("\t\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">").append("\n");
                            sb.append("\t\t\t\t<TR>");
                        }
                        sb.append("<TD PORT=\"p").append(proj._idx).append("\" BGCOLOR=\"yellow\">").append(proj.glabel()).append("</TD>");
                    }
                }
                if (doProjTable) {
                    sb.append("</TR>").append("\n");
                    sb.append("\t\t\t\t</TABLE>").append("\n");
                    sb.append("\t\t\t</TD>");
                }
                sb.append("</TR>\n");
                sb.append("\t\t\t</TABLE>>\n\t\t");

            } else {
                // control nodes have box shape
                // other nodes are ellipses, i.e. default shape
                if( n instanceof CFGNode ) sb.append("shape=box style=filled fillcolor=yellow ");
                else if (n instanceof PhiNode) sb.append("style=filled fillcolor=lightyellow ");
                sb.append("label=\"").append(lab).append("\" ");
            }
            sb.append("];\n");
        }
        if (!_separateControlCluster) {
            // Force Region & Phis to line up
            for (Node n : all) {
                if (n instanceof RegionNode region) {
                    sb.append("\t\t{ rank=same; ");
                    sb.append(region).append(";");
                    for (Node phi : region._outputs)
                        if (phi instanceof PhiNode) sb.append(phi.uniqueName()).append(";");
                    sb.append("}\n");
                }
            }
        }
        sb.append("\t}\n");     // End Node cluster
    }

    private void nodes(StringBuilder sb, Collection<Node> all) {
        nodesByCluster(sb, true, all);
        nodesByCluster(sb, false, all);
    }

    // Build a nested scope display, walking the _prev edge
    private void scope( StringBuilder sb, ScopeNode scope ) {
        sb.append("\tnode [shape=plaintext];\n");
        int level=1;
        //for( int idx = scope._idxs.size()-1; idx>=0; idx-- ) {
        //    var syms = scope._idxs.get(idx);
        //    String scopeName = makeScopeName(scope, level);
        //    sb.append("\tsubgraph cluster_").append(scopeName).append(" {\n"); // Magic "cluster_" in the subgraph name
        //    sb.append("\t\t").append(scopeName).append(" [label=<\n");
        //    sb.append("\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">\n");
        //    // Add the scope level
        //    int scopeLevel = scope._idxs.size()-level;
        //    sb.append("\t\t\t<TR><TD BGCOLOR=\"cyan\">").append(scopeLevel).append("</TD>");
        //    for(String name: syms.keySet())
        //        sb.append("<TD PORT=\"").append(makePortName(scopeName, name)).append("\">").append(name).append("</TD>");
        //    sb.append("</TR>\n");
        //    sb.append("\t\t\t</TABLE>>];\n");
        //    level++;
        //}
        //// Scope clusters nest, so the graphics shows the nested scopes, so
        //// they are not closed as they are printed; so they just keep nesting.
        //// We close them all at once here.
        //sb.append( "\t}\n".repeat( level-1 ) ); // End all Scope clusters
        throw TODO();
    }

    private String makeScopeName(ScopeNode sn, int level) { return sn.uniqueName() + "_" + level; }
    private String makePortName(String scopeName, String varName) { return scopeName + "_" + varName; }

    // Walk the node edges
    private void nodeEdges(StringBuilder sb, Collection<Node> all) {
        // All them edge labels
        sb.append("\tedge [ fontname=Helvetica, fontsize=8 ];\n");
        for( Node n : all ) {
            // Do not display the Constant->Start edge;
            // ProjNodes handled by Multi;
            // ScopeNodes are done separately
            if( n instanceof ConstantNode || n instanceof ProjNode || n instanceof CProjNode || n instanceof ScopeNode || n==Parser.XCTRL )
                continue;
            for( int i=0; i<n.nIns(); i++ ) {
                Node def = n.in(i);
                if( n instanceof PhiNode && def instanceof RegionNode ) {
                    // Draw a dotted use->def edge from Phi to Region.
                    sb.append('\t').append(n.uniqueName());
                    sb.append(" -> ");
                    sb.append(def.uniqueName());
                    sb.append(" [style=dotted taillabel=").append(i).append("];\n");
                } else if( def != null ) {
                    // Most edges land here use->def
                    sb.append('\t').append(n.uniqueName()).append(" -> ");
                    if( def instanceof CProjNode proj ) {
                        String mname = proj.ctrl().uniqueName();
                        sb.append(mname).append(":p").append(proj._idx);
                    } else if( def instanceof ProjNode proj ) {
                        String mname = proj.in(0).uniqueName();
                        sb.append(mname).append(":p").append(proj._idx);
                    } else sb.append(def.uniqueName());
                    // Number edges, so we can see how they track
                    sb.append("[taillabel=").append(i);
                    // The edge from New to ctrl is just for anchoring the New
                    if ( n instanceof NewNode )
                        sb.append(" color=green");
                    // control edges are colored red
                    else if( def instanceof CFGNode )
                        sb.append(" color=red");
                    else if( def.isMem() )
                        sb.append(" color=blue");
                    // Backedges do not add a ranking constraint
                    if( i==2 && (n instanceof PhiNode || n instanceof LoopNode) )
                        sb.append(" constraint=false");
                    sb.append("];\n");
                }
            }
        }
    }

    // Walk the scope edges
    private void scopeEdges( StringBuilder sb, ScopeNode scope ) {
        sb.append("\tedge [style=dashed color=cornflowerblue];\n");
        int level=1;
        //for( int i = scope._idxs.size()-1; i>=0; i-- ) {
        //    var syms = scope._idxs.get(i);
        //    String scopeName = makeScopeName(scope, level);
        //    for( String name : syms.keySet() ) {
        //        int idx = syms.get(name);
        //        Node def = scope.in(idx);
        //        while( def instanceof ScopeNode lazy )
        //            def = lazy.in(idx);
        //        if( def==null ) continue;
        //        sb.append("\t")
        //          .append(scopeName).append(":")
        //          .append('"').append(makePortName(scopeName, name)).append('"') // wrap port name with quotes because $ctrl is not valid unquoted
        //          .append(" -> ");
        //        if( def instanceof CProjNode proj ) {
        //            String mname = proj.ctrl().uniqueName();
        //            sb.append(mname).append(":p").append(proj._idx);
        //        } else sb.append(def.uniqueName());
        //        sb.append(";\n");
        //    }
        //    level++;
        //}
        throw TODO();
    }

    /**
     * Finds all nodes in the graph.
     */
    private Collection<Node> findAll(Node stop, Node scope) {
        final HashMap<Integer, Node> all = new HashMap<>();
        for( Node n : stop._inputs )
            walk(all, n);
        if( scope != null )
            for( Node n : scope._inputs )
                walk(all, n);
        return all.values();
    }

    /**
     * Walk a subgraph and populate distinct nodes in the all list.
     */
    private void walk(HashMap<Integer,Node> all, Node n) {
        if(n == null ) return;
        if (all.get(n._nid) != null) return; // Been there, done that
        all.put(n._nid, n);
        for (Node c : n._inputs)
            walk(all, c);
        for( Node c : n._outputs )
            walk(all, c);
    }
}
