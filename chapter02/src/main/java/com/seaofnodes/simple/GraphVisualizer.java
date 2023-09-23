package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Control;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StartNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphVisualizer {

    Map<Integer, Node> _controls = new HashMap<>();
    Map<Integer, Node> _dataNodes = new HashMap<>();

    public String generateDotOutput(StartNode start) {
        for (int i = 0; i < start.nOuts(); i++) {
            Node n = start.out(i);
            if (!(n instanceof ConstantNode)) {
                walk(n);
            }
        }
        // We should have all nodes in our two maps.
        StringBuilder sb = new StringBuilder();
        sb.append("digraph chapter01\n" +
                "{\n");
        for (Node n : _dataNodes.values()) {
            sb.append('\t').append(n.uniqueName())
                    .append(" [label=\"")
                    .append(n.label())
                    .append("\"];\n");
            walkIns(sb, n);
        }
        sb.append("\tedge [color=red];\n");
        for (Node n : _controls.values()) {
            sb.append('\t').append(n.uniqueName())
                    .append(" [shape=box, label=\"")
                    .append(n.label())
                    .append("\"];\n");
            walkIns(sb, n);
        }
        sb.append("}\n");
        return sb.toString();
    }

    private void walkIns(StringBuilder sb, Node n) {
        for (int i = 0; i < n.nIns(); i++) {
            Node out = n.in(i);
            if (out == null)
                continue;
            sb.append('\t').append(out.uniqueName())
                    .append(" -> ")
                    .append(n.uniqueName())
                    .append(";\n");
        }
    }

    private void walk(Node n) {
        Map<Integer, Node> map;
        List<Node> nodesToVisit = new ArrayList<>();
        if (n instanceof Control) {
            map = _controls;
        } else {
            map = _dataNodes;
        }
        if (map.get(n._nid) == null) {
            // Not yet seen
            map.put(n._nid, n);
            nodesToVisit.add(n);
        }
        for (Node c : n._inputs) {
            if (c != null)
                walk(c);
        }
        for (Node c : nodesToVisit)
            walk(c);
    }
}
