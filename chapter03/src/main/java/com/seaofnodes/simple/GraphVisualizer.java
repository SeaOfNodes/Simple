package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Control;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StartNode;

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
        sb.append("digraph chapter03 {\n");
        
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
        node_edges(sb, all);

        // Walk the Scope edges
        scope_edges(sb, parser._scopes);
        
        sb.append("}\n");
        return sb.toString();
    }


    private void nodes(StringBuilder sb, Collection<Node> all) {
        // Just the Nodes first, in a cluster no edges
        sb.append("\tsubgraph cluster_Nodes {\n"); // Magic "cluster_" in the subgraph name
        for( Node n : all ) {
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            // control nodes have box shape
            // other nodes are ellipses, i.e. default shape
            if( n instanceof Control )
                sb.append("shape=box style=filled fillcolor=yellow ");
            sb.append("label=\"").append(n.label()).append("\" ");
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
            sb.append("\t\t\t<TR><TD BGCOLOR=\"aqua\">").append(level).append("</TD>");
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

    // Walk the node edges
    private void node_edges(StringBuilder sb, Collection<Node> all) {
        for( Node n : all )
            for( Node out : n._inputs )
                if( out != null ) {
                    sb.append('\t').append(n.uniqueName()).append(" -> ").append(out.uniqueName());
                    // Control edges are colored red
                    if( n instanceof ConstantNode && out instanceof StartNode )
                      sb.append(" [style=dotted]");
                    else if( n instanceof Control && out instanceof Control )
                      sb.append(" [color=red]");
                    sb.append(";\n");
                }
    }
    
    // Walk the scope edges
    private void scope_edges( StringBuilder sb, Stack<HashMap<String,Node>> scopes) {
        sb.append("\tedge [style=dashed color=cornflowerblue];\n");
        int level=0;
        for( HashMap<String,Node> scope : scopes ) {
            String scopeName = makeScopeName(level);
            for( String name : scope.keySet() )
                sb.append("\t").append(scopeName).append(":").append(makePortName(scopeName, name)).append(" -> ").append(scope.get(name).uniqueName()).append(";\n");
            level++;
        }
    }
    
    /**
     * Walks the whole graph, starting from Start.
     * Since Start is the input to all constants - we look at the outputs for
     * Start, but for then subsequently we look at the inputs of each node.
     */
    private Collection<Node> findAll(Parser parser) {
        final StartNode start = Parser.START;
        HashMap<Integer, Node> all = new HashMap<>();
        for( Node n : start._outputs )
            walk(all, n);
        for( HashMap<String,Node> scope : parser._scopes )
            for (Node n : scope.values())
                walk(all, n);
        return all.values();
    }

    /**
     * Walk a subgraph and populate distinct nodes in the _all list.
     */
    private void walk(HashMap<Integer, Node> all, Node n) {
        if (all.get(n._nid) != null) return; // Been there, done that
        all.put(n._nid, n);
        for (Node c : n._inputs)
            if (c != null)
                walk(all, c);
    }
}
