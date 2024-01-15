package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

public class GraphEvaluator {

    /**
     * Find the start node from some node in the graph or null if there is no start node
     */
    private static StartNode findStart(BitSet visit, Node node) {
        if (node == null) return null;
        if (node instanceof StartNode start) return start;
        if( visit.get(node._nid) ) return null;
        visit.set(node._nid);
        for( Node def : node._inputs ) {
            var res = findStart(visit,def);
            if( res != null ) return res;
        }
        for( Node use : node._outputs ) {
            var res = findStart(visit,use);
            if( res != null ) return res;
        }
        return null;
    }

    /**
     * Find the control output from a control node
     */
    private static Node findControl(Node control) {
        for( Node use : control._outputs ) {
            if (use.isCFG()) return use;
        }
        return null;
    }

    /**
     * Find the projection for a node
     */
    private static ProjNode findProjection(Node node, int idx) {
        for( Node use : node._outputs ) {
            if (use instanceof ProjNode proj && proj._idx == idx) return proj;
        }
        return null;
    }

    private static RuntimeException timeout() {
        return new RuntimeException("Timeout");
    }

    /**
     * Cache values for phi and parameter projection nodes.
     */
    private final HashMap<Node, Long> cacheValues = new HashMap<>();
    /**
     * Cache for loop phis as they can depend on itself or other loop phis
     */
    private long[] loopPhiCache = new long[16];

    private GraphEvaluator() {
    }

    private long div(DivNode div) {
        long in2 = getValue(div.in(2));
        return in2 == 0 ? 0 : getValue(div.in(1)) / in2;
    }

    /**
     * Calculate the value of a node
     */
    private long getValue(Node node) {
        var cache = cacheValues.get(node);
        if (cache != null) return cache;
        return switch (node) {
            case ConstantNode cons  -> ((TypeInteger)cons.compute()).value();
            case AddNode      add   -> getValue(add.in(1)) + getValue(add.in(2));
            case BoolNode.EQ  eq    -> getValue(eq.in(1)) == getValue(eq.in(2)) ? 1 : 0;
            case BoolNode.LE  le    -> getValue(le.in(1)) <= getValue(le.in(2)) ? 1 : 0;
            case BoolNode.LT  lt    -> getValue(lt.in(1)) <  getValue(lt.in(2)) ? 1 : 0;
            case DivNode      div   -> div(div);
            case MinusNode    minus -> -getValue(minus.in(1));
            case MulNode      mul   -> getValue(mul.in(1)) * getValue(mul.in(2));
            case NotNode      not   -> getValue(not.in(1)) == 0 ? 1 : 0;
            case SubNode      sub   -> getValue(sub.in(1)) - getValue(sub.in(2));
            default                 -> throw Utils.TODO("Unexpected node " + node);
        };
    }

    /**
     * Special case of latchPhis when phis can depend on phis of the same region.
     */
    private void latchLoopPhis(RegionNode region, Node prev) {
        int idx = Utils.find(region._inputs, prev);
        assert idx > 0;
        int i = 0;
        for( Node use : region._outputs ) {
            if (use instanceof PhiNode phi) {
                var value = getValue(phi.in(idx));
                if (i == loopPhiCache.length) loopPhiCache = Arrays.copyOf(loopPhiCache, loopPhiCache.length*2);
                loopPhiCache[i++] = value;
            }
        }
        i = 0;
        for( Node use : region._outputs ) {
            if (use instanceof PhiNode phi) cacheValues.put(phi, loopPhiCache[i++]);
        }
    }

    /**
     * Calculate the values of phis of the region and caches the values. The phis are not allowed to depend on other phis of the region.
     */
    private void latchPhis(RegionNode region, Node prev) {
        int idx = Utils.find(region._inputs, prev);
        assert idx > 0;
        for( Node use : region._outputs ) {
            if (use instanceof PhiNode phi) {
                var value = getValue(phi.in(idx));
                cacheValues.put(phi, value);
            }
        }
    }

    /**
     * Run the graph until either a return is found or the number of loop iterations are done.
     */
    private long evaluate(StartNode start, long parameter, int loops) {
        var parameter1 = findProjection(start, 1);
        if (parameter1 != null) cacheValues.put(parameter1, parameter);
        Node control = findProjection(start, 0);
        Node prev = start;
        while (control != null) {
            Node next;
            switch (control) {
                case RegionNode region -> {
                    if (region instanceof LoopNode && region.in(1) != prev) {
                        if (loops--<=0) throw timeout();
                        latchLoopPhis(region, prev);
                    } else {
                        latchPhis(region, prev);
                    }
                    next = findControl(region);
                }
                case IfNode cond -> next = findProjection(cond, getValue(cond.in(1)) != 0 ? 0 : 1);
                case ReturnNode ret -> {
                    return getValue(ret.in(1));
                }
                case ProjNode ignored -> next = findControl(control);
                default -> throw Utils.TODO("Unexpected control node " + control);
            }
            prev = control;
            control = next;
        }
        return 0;
    }

    public static long evaluate(Node graph) {
        return evaluate(graph, 0);
    }

    public static long evaluate(Node graph, long parameter) {
        return evaluate(graph, parameter, 1000);
    }

    public static long evaluate(Node graph, long parameter, int loops) {
        var start = findStart(new BitSet(), graph);
        if (start == null) throw timeout();
        var evaluator = new GraphEvaluator();
        return evaluator.evaluate(start, parameter, loops);
    }

}
