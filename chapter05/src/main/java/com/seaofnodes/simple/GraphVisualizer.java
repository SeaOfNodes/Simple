package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.util.*;

/**
 * Simple visualizer that outputs GraphViz dot format.
 * The dot output must be saved to a file and run manually via dot to generate the SVG output.
 * Currently, this is done manually.
 */
public class GraphVisualizer {

    public String generateDotOutput(Parser parser) {

        // Since the graph has cycles, we need to create a flat list of all the
        // nodes in the graph.
        Collection<Node> all = findAll(parser);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph chapter05 {\n");
        sb.append("/*\n");
        sb.append(parser.src());
        sb.append("\n*/\n");
        
        // To keep the Scopes below the graph and pointing up into the graph we
        // need to group the Nodes in a subgraph cluster, and the scopes into a
        // different subgraph cluster.  THEN we can draw edges between the
        // scopes and nodes.  If we try to cross subgraph cluster borders while
        // still making the subgraphs DOT gets confused.
        sb.append("\trankdir=BT;\n"); // Force Nodes before Scopes

        // Preserve node input order
        sb.append("\tordering=\"in\";\n");

        // Merge multiple edges hitting the same node.  Makes common shared
        // nodes much prettier to look at.
        sb.append("\tconcentrate=\"true\";\n");
        
        // Just the Nodes first, in a cluster no edges
        nodes(sb, all);
        
        // Now the scopes, in a cluster no edges
        for (ScopeNode sn: parser._allScopes)
            scopes(sb, sn);

        // Walk the Node edges
        nodeEdges(sb, all);

        // Walk the Scope edges
        for (ScopeNode sn: parser._allScopes)
            scopeEdges(sb, sn);
        
        sb.append("}\n");
        return sb.toString();
    }


    private void nodes(StringBuilder sb, Collection<Node> all) {
        // Just the Nodes first, in a cluster no edges
        sb.append("\tsubgraph cluster_Nodes {\n"); // Magic "cluster_" in the subgraph name
        for( Node n : all ) {
            if( n instanceof ProjNode || n instanceof ScopeNode )
                continue; // Do not emit, rolled into MultiNode or Scope cluster already
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            String lab = n.glabel();
            if( n instanceof MultiNode ) {
                // Make a box with the MultiNode on top, and all the projections on the bottom
                sb.append("shape=plaintext label=<\n");
                sb.append("\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\">\n");
                sb.append("\t\t\t<TR><TD BGCOLOR=\"yellow\">").append(lab).append("</TD></TR>\n");
                sb.append("\t\t\t<TR>");
                boolean doProjTable = false;
                for( Node use : n._outputs ) {
                    if( use instanceof ProjNode proj ) {
                        if (!doProjTable) {
                            doProjTable = true;
                            sb.append("<TD>").append("\n");
                            sb.append("\t\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">").append("\n");
                            sb.append("\t\t\t\t<TR>");
                        }
                        sb.append("<TD PORT=\"p").append(proj._idx).append("\"");
                        if( proj.isCFG() ) sb.append(" BGCOLOR=\"yellow\"");
                        sb.append(">").append(proj.glabel()).append("</TD>");
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
                if( n.isCFG() )
                    sb.append("shape=box style=filled fillcolor=yellow ");
                if( n instanceof PhiNode )
                    sb.append("style=filled fillcolor=lightyellow ");
                sb.append("label=\"").append(lab).append("\" ");
            }
            sb.append("];\n");
        }
        
        // Force Region & Phis to line up
        for( Node n : all ) {
            if( n instanceof RegionNode region ) {
                sb.append("\t\t{ rank=same; ");
                sb.append(region).append(";")     ;
                for( Node phi : region._outputs )
                    if( phi instanceof PhiNode )
                        sb.append(phi.uniqueName()).append(";");
                sb.append("}\n");
            }
        }
        
        sb.append("\t}\n");     // End Node cluster
    }

    private void scopes( StringBuilder sb, ScopeNode scopenode) {
        sb.append("\tnode [shape=plaintext];\n");
        int level=0;
        for( HashMap<String,Integer> scope : scopenode._scopes ) {
            String scopeName = makeScopeName(scopenode, level);
            sb.append("\tsubgraph cluster_").append(scopeName).append(" {\n"); // Magic "cluster_" in the subgraph name
            sb.append("\t\t").append(scopeName).append(" [label=<\n");
            sb.append("\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">\n");
            // Add the scope level
            sb.append("\t\t\t<TR><TD BGCOLOR=\"cyan\">").append(level).append("</TD>");
            for( String name : scope.keySet() )
                sb.append("<TD PORT=\"").append(makePortName(scopeName, name)).append("\">").append(name).append("</TD>");
            sb.append("</TR>\n");
            sb.append("\t\t\t</TABLE>>];\n");
            level++;
        }
        // Scope clusters nest, so the graphics shows the nested scopes, so
        // they are not closed as they are printed; so they just keep nesting.
        // We close them all at once here.
        sb.append( "\t}\n".repeat( level ) ); // End all Scope clusters
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
            if( n instanceof ConstantNode || n instanceof ProjNode || n instanceof ScopeNode )
                continue;
            int i=0;
            for( Node def : n._inputs ) {
                // Do not draw the Phi->Region edge (in red); instead Phis are
                // on a line with their Region, and we draw an invisible line
                // from the Region to the Phi, to force all the Phis to the
                // right of the Region.
                if( n instanceof PhiNode && def instanceof RegionNode ) {
                    sb.append('\t').append(def.uniqueName());
                    sb.append(" -> ");
                    sb.append(n.uniqueName());
                    sb.append(" [style=invis]\n");
                    
                } else if( def != null ) {
                    // Most edges land here use->def
                    sb.append('\t').append(n.uniqueName()).append(" -> ");
                    if( def instanceof ProjNode proj ) {
                        String mname = proj.ctrl().uniqueName();
                        sb.append(mname).append(":p").append(proj._idx);
                    } else sb.append(def.uniqueName());
                    // Number edges, so we can see how they track
                    sb.append("[taillabel=").append(i);
                    // control edges are colored red
                    if( def.isCFG() )
                        sb.append("; color=red");
                    sb.append("];\n");
                }
                i++;
            }
        }
    }
    
    // Walk the scope edges
    private void scopeEdges( StringBuilder sb, ScopeNode scopenode ) {
        sb.append("\tedge [style=dashed color=cornflowerblue];\n");
        int level=0;
        for( HashMap<String,Integer> scope : scopenode._scopes ) {
            String scopeName = makeScopeName(scopenode, level);
            for( String name : scope.keySet() ) {
                Node def = scopenode.in(scope.get(name));
                if( def==null ) continue;
                sb.append("\t")
                  .append(scopeName).append(":")
                  .append('"').append(makePortName(scopeName, name)).append('"') // wrap port name with quotes because $ctrl is not valid unquoted
                  .append(" -> ");
                if( def instanceof ProjNode proj ) {
                    String mname = proj.ctrl().uniqueName();
                    sb.append(mname).append(":p").append(proj._idx);
                } else sb.append(def.uniqueName());
                sb.append(";\n");
            }
            level++;
        }
    }
    
    /**
     * Finds all nodes in the graph.
     */
    private Collection<Node> findAll(Parser parser) {
        final StartNode start = Parser.START;
        final HashMap<Integer, Node> all = new HashMap<>();
        for( Node n : start._outputs )
            walk(all, n);
        // Scan symbol tables
        for( HashMap<String,Integer> scope : parser._scope._scopes )
            for (Integer i : scope.values())
                walk(all, parser._scope.in(i));
        return all.values();
    }

    /**
     * Walk a subgraph and populate distinct nodes in the all list.
     */
    private void walk(HashMap<Integer, Node> all, Node n) {
        if(n == null ) return;
        if (all.get(n._nid) != null) return; // Been there, done that
        all.put(n._nid, n);
        for (Node c : n._inputs)
            if (c != null)
                walk(all, c);
        for (Node c : n._outputs)
            if (c != null)
                walk(all, c);
    }
}
