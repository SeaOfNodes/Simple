package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Control;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StartNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple visualizer that outputs GraphViz dot format.
 * The dot output must be saved to a file and run manually via dot to generate the SVG output.
 * Currently, this is done manually.
 */
public class GraphVisualizer {

    public String generateDotOutput(StartNode start) {
        // Since the graph has cycles, we need to create a flat list of all the
        // nodes in the graph.
        Collection<Node> all = findAll(start);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph chapter02\n" +
                "{\n");
        for (Node n : all) {
            sb.append('\t').append(n.uniqueName());
            // control nodes have box shape
            // other nodes are ellipses, i.e. default shape
            if (n instanceof Control)
                sb.append(" [shape=box, ");
            else
                sb.append(" [");
            sb.append(" label=\"")
                    .append(n.label())
                    .append("\"];\n");
            // Output the non control edges
            walkIns(sb, n, false);
        }
        // Now output the control edges which must be in red
        sb.append("\tedge [color=red];\n");
        for (Node n : all) {
            walkIns(sb, n, true);
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Outputs edges. If doControlEdges is true then
     * edges between control nodes are output. Else other edges
     * output.
     */
    private void walkIns(StringBuilder sb, Node in, boolean doControlEdges) {
        for (int i = 0; i < in.nIns(); i++) {
            Node out = in.in(i);
            if (out == null)
                continue;
            boolean isControlEdge = (out instanceof Control) && (in instanceof Control);
            if (doControlEdges && !isControlEdge)
                continue;
            if (!doControlEdges && isControlEdge)
                continue;
            sb.append('\t')
                    .append(out.uniqueName())
                    .append(" -> ")
                    .append(in.uniqueName());
            // the edge from start node to constants is just for convenience so
            // show it differently
            if ((out instanceof StartNode) && (in instanceof ConstantNode))
                sb.append(" [style=dotted]");
            sb.append(";\n");
        }
    }

    /**
     * Walks the whole graph, starting from Start.
     * Since Start is the input to all constants - we look at the outputs for
     * Start, but for then subsequently we look at the inputs of each node.
     */
    private Collection<Node> findAll(StartNode start) {
        Map<Integer, Node> all = new HashMap<>();
        for (int i = 0; i < start.nOuts(); i++) {
            Node n = start.out(i);
            walk(all, n);
        }
        return all.values();
    }

    /**
     * Walk a subgraph and populate distinct nodes in the _all list.
     */
    private void walk(Map<Integer, Node> all, Node n) {
        if (all.get(n._nid) == null) {
            // Not yet seen
            all.put(n._nid, n);
            for (Node c : n._inputs)
                if (c != null)
                    walk(all, c);
        }
    }
}
