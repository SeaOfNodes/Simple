package com.seaofnodes.simple.evaluator;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;

import java.util.*;

/**
 * A late scheduler for test cases.
 * <br>
 * <br>
 * This scheduler is used for tests and is not the final scheduler.
 * <br>
 * <br>
 * The scheduler schedules not as late as possible. Since it is just for test cases this is fine.
 * A better scheduler will try to place nodes outside of loops. Due to the schedule late strategy
 * this scheduler might place initialization code in loops.
 * <br>
 * We schedule late here since memory edges have some implicit information. There can only be one
 * memory edge (for a field) at a time. However, before <code>if</code> statements this might split.
 * When scheduling early a store might be moved out of the loop but then there are two edges alive.
 * The one through the store node and the adjacent one. This is an invalid schedule and instead of
 * handling this case a late scheduler does not have this problem as memory edges are combined
 * through phi nodes.
 * <br>
 * <br>
 * A bit about this scheduler. It first discovers all alive nodes and allocates side data for them.
 * Then the control flow graph is build. Then, all phis are placed and finally all the remaining nodes
 * in an order where a node is placed when all alive uses are placed. Finally, the output is produced.
 */
public class Scheduler {

    /**
     * A basic block with a schedule of the containing nodes.
     *
     * @param nodes The nodes in the final schedule of this block
     * @param exit The exit node of this block.
     * @param exitId In case the next block is a region this gives the entry id into the region.
     * @param next The next blocks.
     */
    public record Block(Node[] nodes, Node exit, int exitId, Block[] next) {}

    /**
     * Definition of a block in the building process.
     */
    private static class BasicBlock {

        /**
         * Dominator of this block
         */
        final BasicBlock dom;
        /**
         * Id of this block where <code>a.depth < b.depth</code> means that a is before b or
         * both are in different branches.
         */
        final int depth;
        /**
         * Previous blocks
         */
        final BasicBlock[] prev;
        /**
         * Entry to this block
         */
        final Node entry;
        /**
         * Schedules node in reverse order.
         */
        final ArrayList<Node> reverseSchedule = new ArrayList<>();

        /**
         * Initialize the block.
         * @param entry The node that started this block
         * @param depth The depth of this node. All previous nodes need to have a lower value.
         * @param prev The previous blocks.
         */
        BasicBlock(Node entry, int depth, BasicBlock... prev) {
            BasicBlock dom = null;
            int max = -1;
            for(var bb: prev) {
                max = Math.max(max, bb.depth);
                dom = dom==null?bb:dom(dom, bb);
            }
            assert depth > max;
            this.dom = dom;
            this.depth = depth;
            this.entry = entry;
            this.prev = prev;
        }
        BasicBlock(Node entry, BasicBlock prev) {
            this(entry,prev.depth+1,prev);
        }

    }

    /**
     * Ancillary data for nodes used during the scheduling process.
     */
    private static class NodeData {

        /**
         * The node this ancillary data is used for.
         */
        final Node node;

        /**
         * Number of alive users not yet scheduled.
         */
        int users = 1;

        /**
         * Block into which the node should be placed so far.
         * This will be updated with every placed use.
         */
        BasicBlock block;

        /**
         * Initialize ancillary data for a node
         * @param node The node this ancillary data is associated with.
         */
        NodeData(Node node) {
            this.node = node;
        }

    }

    /**
     * Ancillary data for nodes.
     */
    private final IdentityHashMap<Node, NodeData> data = new IdentityHashMap<>();
    /**
     * List of nodes which can be placed since all users are placed.
     */
    private final Stack<NodeData> scheduleQueue = new Stack<>();

    /**
     * Get the ancillary data for an alive node.
     * @param node The node for which the ancillary data is requested.
     * @return The ancillary data for the node.
     */
    NodeData d(Node node) {
        return od(node).orElseThrow(AssertionError::new);
    }

    /**
     * Get the ancillary data for a potentially dead node.
     * @param node The node for which the ancillary data is requested.
     * @return The ancillary data for the node an alive node or
     * <code>Optional.empty()</code> for <code>null</code> or dead nodes.
     */
    Optional<NodeData> od(Node node) {
        return Optional.ofNullable(data.get(node));
    }

    /**
     * Checks a node for validity
     * @param data The node to check.
     * @return true if all placed inputs are before this node.
     */
    private boolean isValid(NodeData data) {
        return data.node._inputs.stream()
                .map(i->i==null?null:d(i))
                .map(d->d==null||d.users>0?null:d.block)
                .allMatch(d->d==null||dom(d, data.block)==d);
    }

    /**
     * Checks that node is not XCtrl.
     * @param node The node to check.
     * @return true if the node is not XCtrl.
     */
    private static boolean isNotXCtrl(Node node) {
        return !(node instanceof ConstantNode c && c.compute() == Type.XCONTROL) && !(node instanceof XCtrlNode);
    }

    /**
     * Return the dominator of <code>a</code> and <code>b</code>
     * @param a Block a
     * @param b Block b
     * @return The dominator of <code>a</code> and <code>b</code>
     */
    private static BasicBlock dom(BasicBlock a, BasicBlock b) {
        while (a!=b) {
            if (a.depth >= b.depth) a=a.dom;
            if (b.depth > a.depth) b=b.dom;
        }
        return a;
    }

    /**
     * Return if a is before b.
     * @param a Block a
     * @param b Block b
     * @return true is a is before b.
     */
    private static boolean isBefore(BasicBlock a, BasicBlock b) {
        while (b.depth > a.depth) {
            while (b.dom.depth > a.depth) b = b.dom;
            for (int i=0; i<b.prev.length-1; i++) {
                if (isBefore(a, b.prev[i])) return true;
            }
            b = b.prev[b.prev.length-1];
        }
        return a == b;
    }

    /**
     * Is node placed during the control flow graph build.
     * @param data Node to check.
     * @return true if node is placed during the control flow graph build.
     */
    private static boolean isPinnedNode(NodeData data) {
        return data.node instanceof CFGNode || data.node instanceof PhiNode;
    }

    /**
     * Refine placement of a node.
     * @param data The node to refine.
     * @param block The block before the node should be scheduled.
     */
    private void refinePlacement(NodeData data, BasicBlock block) {
        assert !isPinnedNode(data);
        data.block = data.block == null ? block : dom(data.block, block);
        assert isValid(data);
    }

    /**
     * Refine placement if before is happening before data
     * @param data The node for which the placement should be refined.
     * @param before The node before which the data node should be placed.
     */
    private void optionalRefinePlacement(NodeData data, Node before) {
        od(before).ifPresent(d->{
            // before might be in a different branch. So check this case with isBefore
            // and only refine the placement if it is not in a different branch.
            if (isBefore(d.block, data.block)) refinePlacement(data, d.block);
        });
    }

    /**
     * Schedule all nodes not yet scheduled.
     */
    private void doSchedule() {
        while (!scheduleQueue.isEmpty()) {
            var data = scheduleQueue.pop();
            assert !isPinnedNode(data);

            assert data.block != null;
            assert data.users == 0;

            if (data.node instanceof MemOpNode l && l._isLoad) {
                assert isValid(data);
                // Handle anti-deps of load nodes.
                // At this point all anti-dep nodes are scheduled,
                // but they did not refine the placement
                // so do that now.
                var mem = data.node.in(1);
                for(var out : mem._outputs) {
                    if (out instanceof PhiNode p) {
                        var r = p.in(0);
                        for (int i = 1; i < p.nIns(); i++) {
                            if (p.in(i) == mem) optionalRefinePlacement(data, r.in(i));
                        }
                    } else if (!(out instanceof MemOpNode ld && ld._isLoad)) {
                        optionalRefinePlacement(data, out);
                    }
                }
            }

            assert isValid(data);
            data.block.reverseSchedule.add(data.node);

            for(var in:data.node._inputs) {
                if (in!=null) update(d(in), data.block);
            }
            if (data.node instanceof MemOpNode s && !s._isLoad) {
                // Store nodes have anti-deps to load nodes.
                // So decrease the uses of these loads when the store is placed.
                for (var out: ((Node)s).in(1)._outputs) {
                    if (out instanceof MemOpNode ld && ld._isLoad) od(out).ifPresent(this::decUsers);
                }
            }
        }

        // Now all nodes should be placed and have a block assigned
        assert this.data.values().stream().noneMatch(d->d.block==null);
    }

    /**
     * Decrement the not-yet-placed users of a block.
     * @param data The node for which the users should be decremented.
     */
    private void decUsers(NodeData data) {
        assert data.users > 0;
        if (--data.users == 0) {
            assert data.block != null;
            assert isValid(data);

            // When all users are gone this node can be scheduled.
            scheduleQueue.push(data);
        }
    }

    /**
     * Update the placement of a node
     * @param data The node to update
     * @param block The block before which the node should happen.
     */
    private void update(NodeData data, BasicBlock block) {
        assert block != null;
        if (data.users == 0) {
            assert isPinnedNode(data);
            return;
        }
        refinePlacement(data, block);
        decUsers(data);
    }

    /**
     * Checks if a CFG node is ready to be placed. This is used for regions joining branches.
     * @param node The CFG node to check
     * @return true if all parents of a CFG node are placed.
     */
    private boolean isCFGNodeReady(Node node) {
        if (node instanceof LoopNode l) {
            assert d(l.in(1)).block != null;
        } else if (node instanceof RegionNode r) {
            for(int i=1; i<r.nIns(); i++) {
                if (od(r.in(i)).map(d->d.block==null).orElse(false)) return false;
            }
        } else if (node instanceof XCtrlNode) {
            return false;
        } else {
            assert d(node.in(0)).block != null;
        }
        return true;
    }

    /**
     * Schedule all phi nodes.
     * @param phiQueue List of all the phi nodes to schedule.
     */
    private void schedulePhis(Stack<NodeData> phiQueue) {
        while(!phiQueue.empty()) {
            var data = phiQueue.pop();
            var phi = (PhiNode) data.node;
            var r = phi.in(0);
            data.block = d(r).block;
            for(int i=1; i<phi.nIns(); i++) {
                var in = phi.in(i);
                od(r.in(i)).ifPresent(d->update(d(in), d.block));
            }
        }
    }

    /**
     * Build the control flow graph.
     * @param start The start node.
     */
    private void doBuildCTF(StartNode start) {
        var queue = new Stack<NodeData>();
        var phiQueue = new Stack<NodeData>();
        queue.push(d(start));
        while (!queue.isEmpty()) {
            var data = queue.pop();
            var node = data.node;
            assert node instanceof CFGNode;
            BasicBlock block = switch (node) {
            case StartNode s -> new BasicBlock(s, 0);
            case LoopNode l -> new BasicBlock(l, d(l.in(1)).block);
            case RegionNode r -> {
                    var prev = new ArrayList<BasicBlock>();
                    int depth = 0;
                    for(int i=1; i<r.nIns(); i++) {
                        if( !od(r.in(i)).isPresent() ) continue;
                        BasicBlock bb = d(r.in(i)).block;
                        prev.add(bb);
                        depth = Math.max(depth,bb.depth);
                    }
                    yield new BasicBlock(r, depth+1, prev.toArray(BasicBlock[]::new));
            }
            case IfNode i -> d(i.in(0)).block;
            case ReturnNode ret -> d(ret .in(0)).block;
            case CallNode call -> throw Utils.TODO();
            default -> new BasicBlock(node, d(node.in(0)).block);
            };
            data.block = block;
            if (node instanceof RegionNode r) {
                // Regions might have phis which need to be scheduled.
                // Put them on a list for later scheduling.
                for (var out:r._outputs) {
                    if (out instanceof PhiNode p) {
                        var d = this.data.get(p);
                        if (d!=null) {
                            d.users=0;
                            phiQueue.push(d);
                        }
                    }
                }
            }
            if( node instanceof FunNode fun ) {
                // Cannot find CFG user from fun via a naive search, because always find
                // Return but Return is not always the one CFG user.
                CFGNode n = fun.uctrl();
                assert isCFGNodeReady(n) && d(n).block==null;
                queue.push(d(n));
            } else if( !(node instanceof ReturnNode) ) {
                for (Node n : node._outputs)
                    if(n instanceof CFGNode && isCFGNodeReady(n) && d(n).block == null)
                        queue.push(d(n));
            }
            var b = block;
            for(var in:data.node._inputs) od(in).ifPresent(d->update(d, b));
        }

        // Schedule all phi nodes.
        schedulePhis(phiQueue);
    }

    /**
     * Mark a node alive and create ancillary data for it.
     * @param queue List into which this node should be placed if it needs to be visited.
     * @param node Node which should be marked alive.
     * @param cfg If this node is a CFG node.
     */
    private void markAlive(Stack<NodeData> queue, Node node, boolean cfg) {
        var nd = od(node).orElse(null);
        if (nd != null) {
            // Node was already visited, just increase the users.
            if (nd.users>0) nd.users++;
            return;
        }
        assert node instanceof CFGNode == cfg;
        assert isNotXCtrl(node);
        nd = new NodeData(node);
        if (cfg) nd.users=0;
        data.put(node, nd);
        queue.push(nd);
    }

    /**
     * Mark all alive nodes.
     * First all CFG nodes are marked, then all other nodes.
     * @param node THe start node.
     */
    private void doMarkAlive(Node node) {
        var cfgQueue = new Stack<NodeData>();
        var dataQueue = new Stack<NodeData>();
        var mem = new Stack<NodeData>();
        markAlive(cfgQueue, node, true);
        // Mark all CFG nodes.
        while (!cfgQueue.isEmpty()) {
            var data = cfgQueue.pop();
            node = data.node;
            assert node instanceof CFGNode;
            if (!(node instanceof ReturnNode) ) {
                for (var out : node._outputs) if (out!=null && out instanceof CFGNode && isNotXCtrl(out)) markAlive(cfgQueue, out, true);
            }
            for (var in : node._inputs) if(in!=null && !(in instanceof CFGNode) && isNotXCtrl(in)) markAlive(dataQueue, in, false);
        }
        // Mark all other nodes.
        while (!dataQueue.isEmpty()) {
            var data = dataQueue.pop();
            node = data.node;
            assert !(node instanceof CFGNode);
            if (node instanceof PhiNode phi) {
                var r = phi.in(0);
                for (int i=1; i<phi.nIns(); i++) {
                    if (od(r.in(i)).isPresent()) markAlive(dataQueue, phi.in(i), false);
                }
            } else {
                for (var in : node._inputs) if (in != null && !(in instanceof CFGNode)) markAlive(dataQueue, in, false);
            }
            if (node instanceof MemOpNode s && !s._isLoad) mem.push(data);
        }
        // Handle store nodes and increase load with an anti-dep to the store.
        while (!mem.isEmpty()) {
            var data = mem.pop();
            node = data.node;
            for(var out:node.in(1)._outputs) {
                if (out instanceof MemOpNode ld && ld._isLoad) od(out).ifPresent(d->d.users++);
            }
        }
    }

    /**
     * Helper function to append the nodes of a block to an array of nodes
     * @param arr The array the nodes of a block should be appended to.
     * @param node A potentially separator node
     * @param block The block from which the nodes should be appended.
     * @return The combined new array.
     */
    private static Node[] appendNodes(Node[] arr, Node node, BasicBlock block) {
        var idx = arr.length;
        arr = Arrays.copyOf(arr, arr.length+block.reverseSchedule.size()+(node==null?0:1));
        if (node != null) arr[idx++] = node;
        for(int i=block.reverseSchedule.size()-1;i>=0;i--) {
            arr[idx++] = block.reverseSchedule.get(i);
        }
        return arr;
    }

    /**
     * Find the CFG output of a node
     * @param node The node
     * @return The CFG output of the node.
     */
    private static CFGNode findSingleCFGOut(Node node) {
        if (node instanceof StartNode) {
            for(var n:node._outputs)
                if (n instanceof FunNode fun && "main".equals(fun._name) )
                   return fun;
            return null;
        }
        if( node instanceof FunNode fun ) return fun.uctrl();
        assert node._outputs.stream().filter(x -> x instanceof CFGNode).limit(2).count()<=1;
        for(var n:node._outputs) if(n instanceof CFGNode cfg) return cfg;
        return null;
    }

    /**
     * Build the final data structure after all the scheduling happened.
     * @param start The start node
     * @return The final schedule.
     */
    private Block build(Node start) {
        var blocks = new IdentityHashMap<Node, Block>();
        var queue = new Stack<NodeData>();
        queue.push(d(start));

        // Visit all CFG nodes and create blocks for them.
        // This can combine blocks.
        while(!queue.isEmpty()) {
            var data = queue.pop();
            var first = data.node;
            Node prev;
            var last = first;
            Node[] arr = new Node[0];
            if (first instanceof RegionNode) {
                var nodes = new ArrayList<Node>();
                for(var out: first._outputs) {
                    if (out instanceof PhiNode) {
                        if(od(out).isPresent()) nodes.add(out);
                    }
                }
                arr = nodes.toArray(arr);
            }
            arr = appendNodes(arr, null, data.block);
            assert !(first instanceof IfNode);
            for(;;) {
                prev = last;
                last = findSingleCFGOut(last);
                if (last == null) break;
                if (last instanceof RegionNode) {
                    if (blocks.get(last) == null && last != first)
                        queue.push(d(last));
                    break;
                }
                if (last instanceof ReturnNode) break;
                if (last instanceof IfNode if_) {
                    for(var out:if_._outputs) if (out instanceof CFGNode && blocks.get(out) == null) queue.push(d(out));
                    break;
                }
                data = d(last);
                arr = appendNodes(arr, data.node, data.block);
            }
            blocks.put(first, switch(last) {
                case null -> new Block(arr, null, -1, new Block[0]);
                case IfNode i -> new Block(arr, i, -1, new Block[2]);
                case RegionNode r -> new Block(arr, r, r._inputs.find(prev), new Block[1]);
                case ReturnNode r -> new Block(arr, r   , -1, new Block[0]);
                default -> throw new AssertionError("Unexpected block exit node");
            });
        }
        // Update the next pointer of all blocks.
        for (var block: blocks.values()) {
            switch(block.exit) {
                case null: break;
                case IfNode i:
                    for(var out:i._outputs) if (out instanceof CProjNode p) block.next[p._idx] = blocks.get(p);
                    break;
                case RegionNode r:
                    block.next[0] = blocks.get(r);
                    break;
                default:
                    assert block.exit instanceof ReturnNode;
            }
        }
        // And return the block for the start node.
        return blocks.get(start);
    }

    /**
     * Create a schedule for the program reachable from start.
     * @param start The start node
     * @return The final schedule.
     */
    public static Scheduler.Block schedule(StartNode start) {
        var scheduler = new Scheduler();
        scheduler.doMarkAlive(start);
        scheduler.doBuildCTF(start);
        scheduler.doSchedule();
        return scheduler.build(start);
    }

}
