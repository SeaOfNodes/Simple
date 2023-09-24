package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Control;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StartNode;

import java.util.HashMap;
import java.util.Map;

public class GraphVisualizer {

    Map<Integer, Node> _all = new HashMap<>();

    public String generateDotOutput(StartNode start) {
        for (int i = 0; i < start.nOuts(); i++) {
            Node n = start.out(i);
            walk(n);
        }
        // We should have all nodes in our two maps.
        StringBuilder sb = new StringBuilder();
        sb.append("digraph chapter02\n" +
                "{\n");
        for (Node n : _all.values()) {
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
        for (Node n : _all.values()) {
            walkIns(sb, n, true);
        }
        sb.append("}\n");
        return sb.toString();
    }

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

    private void walk(Node n) {
        if (_all.get(n._nid) == null) {
            // Not yet seen
            _all.put(n._nid, n);
            for (Node c : n._inputs)
                if (c != null)
                    walk(c);
        }
    }
}
