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
        sb.append("digraph chapter02 {\n");
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

        // Walk the Node edges
        nodeEdges(sb, all);

        sb.append("}\n");
        return sb.toString();
    }


    private void nodes(StringBuilder sb, Collection<Node> all) {
        // Just the Nodes first, in a cluster no edges
        sb.append("\tsubgraph cluster_Nodes {\n"); // Magic "cluster_" in the subgraph name
        for( Node n : all ) {
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            String lab = n.glabel();
            // control nodes have box shape
            // other nodes are ellipses, i.e. default shape
            if( n.isCFG() )
                sb.append("shape=box style=filled fillcolor=yellow ");
            sb.append("label=\"").append(lab).append("\" ");
            sb.append("];\n");
        }
        sb.append("\t}\n");     // End Node cluster
    }


    // Walk the node edges
    private void nodeEdges(StringBuilder sb, Collection<Node> all) {
        // All them edge labels
        sb.append("\tedge [ fontname=Helvetica, fontsize=8 ];\n");
        for( Node n : all ) {
            // In this chapter we do display the Constant->Start edge;
            int i=0;
            for( Node def : n._inputs ) {
                if( def != null ) {
                    // Most edges land here use->def
                    sb.append('\t').append(n.uniqueName()).append(" -> ");
                    sb.append(def.uniqueName());
                    // Number edges, so we can see how they track
                    sb.append("[taillabel=").append(i);
                    if( n instanceof ConstantNode && def instanceof StartNode )
                        sb.append(" style=dotted");
                    // control edges are colored red
                    else if( def.isCFG() )
                        sb.append(" color=red");
                    sb.append("];\n");
                }
                i++;
            }
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
