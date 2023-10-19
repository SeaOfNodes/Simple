package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeControl;

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
        sb.append("digraph chapter04 {\n");
        sb.append("/*\n");
        sb.append(parser.src());
        sb.append("\n*/\n");
        
        // To keep the Scopes below the graph and pointing up into the graph we
        // need to group the Nodes in a subgraph cluster, and the scopes into a
        // different subgraph cluster.  THEN we can draw edges between the
        // scopes and nodes.  If we try to cross subgraph cluster borders while
        // still making the subgraphs DOT gets confused.
        sb.append("\trankdir=BT;\n"); // Force Nodes before Scopes
        
        // Just the Nodes first, in a cluster no edges
        nodes(sb, all);
        
        // Now the scopes, in a cluster no edges
        scopes(sb, parser._scopes);

        // Walk the Node edges
        nodeEdges(sb, all);

        // Walk the Scope edges
        scopeEdges(sb, parser._scopes);
        
        sb.append("}\n");
        return sb.toString();
    }


    private void nodes(StringBuilder sb, Collection<Node> all) {
        // Just the Nodes first, in a cluster no edges
        sb.append("\tsubgraph cluster_Nodes {\n"); // Magic "cluster_" in the subgraph name
        for( Node n : all ) {
            if( n instanceof ProjNode proj )
                continue; // Do not emit, rolled into MultiNode already
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            String lab = n.label();
            if( n instanceof MultiNode ) {
                int pouts=0;
                for( Node proj : n._outputs )
                    if( proj instanceof ProjNode )
                        pouts++;
                // Make a box with the MultiNode on top, and all the projections on the bottom
                sb.append("shape=plaintext label=<\n");
                sb.append("\t\t\t<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">\n");
                sb.append("\t\t\t<TR><TD COLSPAN=\"").append(pouts).append("\" BGCOLOR=\"yellow\">").append(lab).append("</TD></TR>\n");
                sb.append("\t\t\t<TR>");
                for( Node use : n._outputs ) {
                    if( use instanceof ProjNode proj ) {
                        sb.append("<TD PORT=\"p").append(proj._idx).append("\"");
                        if( proj._idx==0 ) sb.append(" BGCOLOR=\"yellow\"");
                        sb.append(">").append(proj.label()).append("</TD>");
                    }
                }
                sb.append("</TR>\n");
                sb.append("\t\t\t</TABLE>>\n\t\t");
                
            } else {
                // control nodes have box shape
                // other nodes are ellipses, i.e. default shape
                if( n instanceof Control )
                    sb.append("shape=box style=filled fillcolor=yellow ");
                sb.append("label=\"").append(lab).append("\" ");
            }
            sb.append("];\n");
        }
        sb.append("\t}\n");     // End Node cluster
    }

    private void scopes( StringBuilder sb, Stack<HashMap<String,Node>> scopes) {
        sb.append("\tnode [shape=plaintext];\n");
        int level=0;
        for( HashMap<String,Node> scope : scopes ) {
            sb.append("\tsubgraph cluster_").append(level).append(" {\n"); // Magic "cluster_" in the subgraph name
            String scopeName = makeScopeName(level);
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
    
    private String makeScopeName(int level) { return "scope" + level; }
    private String makePortName(String scopeName, String varName) { return scopeName + "_" + varName; }

    private boolean isControlEdge(Node def) {
        return def._type instanceof TypeControl;
    }

    // Walk the node edges
    private void nodeEdges(StringBuilder sb, Collection<Node> all) {
        for( Node n : all ) {
            if( n instanceof ConstantNode || n instanceof ProjNode )
                continue;       // Do not display the Constant->Start edge; ProjNodes handled by Multi
            for( Node def : n._inputs )
                if( def != null ) {
                    sb.append('\t').append(n.uniqueName()).append(" -> ");
                    if( def instanceof ProjNode proj ) {
                        String mname = proj.ctrl().uniqueName();
                        sb.append(mname).append(":p").append(proj._idx);
                    } else sb.append(def.uniqueName());
                    // Control edges are colored red
                    if( isControlEdge(def) )
                        sb.append(" [color=red]");
                    sb.append(";\n");
                }
        }
    }
    
    // Walk the scope edges
    private void scopeEdges( StringBuilder sb, Stack<HashMap<String,Node>> scopes) {
        sb.append("\tedge [style=dashed color=cornflowerblue];\n");
        int level=0;
        for( HashMap<String,Node> scope : scopes ) {
            String scopeName = makeScopeName(level);
            for( String name : scope.keySet() ) {
                sb.append("\t")
                  .append(scopeName).append(":")
                  .append('"').append(makePortName(scopeName, name)).append('"') // wrap port name with quotes because $ctrl is not valid unquoted
                  .append(" -> ");
                Node def = scope.get(name);
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
        for( HashMap<String,Node> scope : parser._scopes )
            for (Node n : scope.values())
                walk(all, n);
        return all.values();
    }

    /**
     * Walk a subgraph and populate distinct nodes in the all list.
     */
    private void walk(HashMap<Integer, Node> all, Node n) {
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
