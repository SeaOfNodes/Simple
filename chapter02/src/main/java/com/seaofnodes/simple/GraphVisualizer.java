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
        sb.append("digraph chapter02 {\n");

        // To keep the Scopes below the graph and pointing up into the graph we
        // need to group the Nodes in a subgraph cluster, and the scopes into a
        // different subgraph cluster.  THEN we can draw edges between the
        // scopes and nodes.  If we try to cross subgraph cluster borders while
        // still making the subgraphs DOT gets confused.
        sb.append("\trankdir=BT;\n"); // Force Nodes before Scopes

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
        for (Node n : all) {
            sb.append("\t\t").append(n.uniqueName()).append(" [ ");
            // control nodes have box shape
            // other nodes are ellipses, i.e. default shape
            if (n instanceof Control)
                sb.append("shape=box style=filled fillcolor=yellow ");
            sb.append("label=\"").append(n.label()).append("\" ");
            sb.append("];\n");
        }
        sb.append("\t}\n");     // End Node cluster
    }


    // Walk the node edges
    private void nodeEdges(StringBuilder sb, Collection<Node> all) {
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

    /**
     * Walks the whole graph, starting from Start.
     * Since Start is the input to all constants - we look at the outputs for
     * Start, but for then subsequently we look at the inputs of each node.
     * During graph construction not all nodes are reachable this way, so we
     * also scan the symbol tables.
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
    }
}
