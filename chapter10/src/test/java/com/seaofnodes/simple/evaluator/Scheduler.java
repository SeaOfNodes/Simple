package com.seaofnodes.simple.evaluator;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Optional;

public class Scheduler {

    public record Block(Node[] nodes, Node exit, int exitId, Block[] next) {}

    private static class BuildBlock {

        final BuildBlock dom;
        final int depth;
        final Node entry;
        final ArrayList<Node> reverseSchedule = new ArrayList<>();

        BuildBlock(BuildBlock dom, Node entry) {
            this.dom = dom;
            this.depth = dom == null ? 0 : dom.depth+1;
            this.entry = entry;
        }

    }

    private static class NodeData {

        final Node node;

        NodeData next;

        int users = 1;

        BuildBlock block;

        NodeData(Node node) {
            this.node = node;
        }

    }

    private static class NodeQueue {

        private NodeData first;

        void push(NodeData node) {
            assert node.next == null;
            node.next = first;
            first = node;
        }

        NodeData pop() {
            var n = first;
            if (n != null) {
                first = n.next;
                n.next = null;
            }
            return n;
        }

    }

    private final IdentityHashMap<Node, NodeData> data = new IdentityHashMap<>();
    private final NodeQueue scheduleQueue = new NodeQueue();

    NodeData d(Node node) {
        return od(node).orElseThrow(AssertionError::new);
    }

    Optional<NodeData> od(Node node) {
        return Optional.ofNullable(data.get(node));
    }

    private boolean isValid(NodeData data) {
        return data.node._inputs.stream().map(i->i==null?null:d(i)).map(d->d==null||d.users>0?null:d.block).allMatch(d->d==null||dom(d, data.block)==d);
    }

    private static boolean isNotXCtrl(Node node) {
        return !(node instanceof ConstantNode c && c.compute() == Type.XCONTROL);
    }

    private static BuildBlock dom(BuildBlock a, BuildBlock b) {
        while (a!=b) {
            if (a.depth >= b.depth) a=a.dom;
            if (b.depth > a.depth) b=b.dom;
        }
        return a;
    }

    private void optionalRefinePlacement(NodeData data, Node before) {
        od(before).ifPresent(d->{
            var b = dom(data.block, d.block);
            if (d.block == b) data.block = b;
        });
    }

    private static boolean isPinnedNode(NodeData data) {
        return data.node.isCFG() || data.node instanceof PhiNode;
    }

    private void refinePlacement(NodeData data, BuildBlock block) {
        assert !isPinnedNode(data);
        data.block = data.block == null ? block : dom(data.block, block);
        assert isValid(data);
    }

    private void doSchedule() {
        NodeData data;
        while ((data=scheduleQueue.pop()) != null) {
            assert !isPinnedNode(data);

            assert data.block != null;
            assert data.users == 0;

            if (data.node instanceof LoadNode l) {
                assert isValid(data);
                var mem = l.in(1);
                for(var out : mem._outputs) {
                    if (out instanceof PhiNode p) {
                        var r = p.in(0);
                        for(int i=1; i<p.nIns(); i++) {
                            if (p.in(i) == mem) optionalRefinePlacement(data, r.in(i));
                        }
                    } else if (!(out instanceof LoadNode)) {
                        optionalRefinePlacement(data, out);
                    }
                }
            }

            assert isValid(data);
            data.block.reverseSchedule.add(data.node);

            for(var in:data.node._inputs) {
                if (in!=null) update(d(in), data.block);
            }
            if (data.node instanceof StoreNode s) {
                for (var out: s.in(1)._outputs) {
                    if (out instanceof LoadNode) od(out).ifPresent(this::decUsers);
                }
            }
        }
        assert this.data.values().stream().noneMatch(d->d.block==null);
    }

    private void decUsers(NodeData data) {
        assert data.users > 0;
        if (--data.users == 0) {
            assert data.block != null;
            assert isValid(data);
            scheduleQueue.push(data);
        }
    }

    private void update(NodeData data, BuildBlock block) {
        assert block != null;
        if (data.users == 0) {
            assert isPinnedNode(data);
            return;
        }
        refinePlacement(data, block);
        decUsers(data);
    }

    private boolean isCFGNodeReady(Node node) {
        if (node instanceof LoopNode l) {
            assert d(l.in(1)).block != null;
        } else if (node instanceof RegionNode r) {
            for(int i=1; i<r.nIns(); i++) {
                if (od(r.in(i)).map(d->d.block==null).orElse(false)) return false;
            }
        } else {
            assert d(node.in(0)).block != null;
        }
        return true;
    }

    private void schedulePhis(NodeQueue phiQueue) {
        NodeData data;
        while((data=phiQueue.pop()) != null) {
            var phi = (PhiNode) data.node;
            var r = phi.in(0);
            data.block = d(r).block;
            for(int i=1; i<phi.nIns(); i++) {
                var in = phi.in(i);
                od(r.in(i)).ifPresent(d->update(d(in), d.block));
            }
        }
    }

    private void doBuildCTF(StartNode start) {
        var queue = new NodeQueue();
        var phiQueue = new NodeQueue();
        queue.push(d(start));
        NodeData data;
        while ((data=queue.pop())!=null) {
            var node = data.node;
            assert node.isCFG();
            BuildBlock block;
            switch (node) {
                case StartNode s:
                    block = new BuildBlock(null, s);
                    break;
                case LoopNode l:
                    block = new BuildBlock(d(l.in(1)).block, l);
                    break;
                case RegionNode r:
                    block = null;
                    for(int i=1; i<r.nIns(); i++) {
                        var d = od(r.in(i)).orElse(null);
                        if (d != null) {
                            block = block == null ? d.block : dom(block, d.block);
                        }
                    }
                    block = new BuildBlock(block, r);
                    break;
                case IfNode i:
                    block = d(i.in(0)).block;
                    break;
                case ReturnNode r:
                    block = d(r.in(0)).block;
                    break;
                default:
                    block = new BuildBlock(d(node.in(0)).block, node);
            }
            data.block = block;
            if (node instanceof RegionNode r) {
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
            if (!(node instanceof ReturnNode))
                for (Node n:node._outputs) if (n.isCFG() && isCFGNodeReady(n) && d(n).block == null) queue.push(d(n));
            var b = block;
            for(var in:data.node._inputs) od(in).ifPresent(d->update(d, b));
        }
        schedulePhis(phiQueue);
    }

    private void markAlive(NodeQueue queue, Node node, boolean cfg) {
        var nd = od(node).orElse(null);
        if (nd != null) {
            if (nd.users>0) nd.users++;
            return;
        }
        assert node.isCFG() == cfg;
        assert isNotXCtrl(node);
        nd = new NodeData(node);
        if (cfg) nd.users=0;
        data.put(node, nd);
        queue.push(nd);
    }

    private void doMarkAlive(Node node) {
        var cfgQueue = new NodeQueue();
        var dataQueue = new NodeQueue();
        var mem = new NodeQueue();
        markAlive(cfgQueue, node, true);
        NodeData data;
        while ((data=cfgQueue.pop()) != null) {
            node = data.node;
            assert node.isCFG();
            if (!(node instanceof ReturnNode)) {
                for (var out : node._outputs) if (out.isCFG() && isNotXCtrl(out)) markAlive(cfgQueue, out, true);
            }
            for (var in : node._inputs) if(in!=null && !in.isCFG() && isNotXCtrl(in)) markAlive(dataQueue, in, false);
        }
        while ((data=dataQueue.pop()) != null) {
            node = data.node;
            assert !node.isCFG();
            if (node instanceof PhiNode phi) {
                var r = phi.in(0);
                for (int i=1; i<phi.nIns(); i++) {
                    if (od(r.in(i)).isPresent()) markAlive(dataQueue, phi.in(i), false);
                }
            } else {
                for (var in : node._inputs) if (in != null && !in.isCFG()) markAlive(dataQueue, in, false);
            }
            if (node instanceof StoreNode) mem.push(data);
        }
        while ((data=mem.pop()) != null) {
            node = data.node;
            for(var out:node.in(1)._outputs) {
                if (out instanceof LoadNode) od(out).ifPresent(d->d.users++);
            }
        }
    }

    private static Node[] appendNodes(Node[] arr, Node node, BuildBlock block) {
        var idx = arr.length;
        arr = Arrays.copyOf(arr, arr.length+block.reverseSchedule.size()+(node==null?0:1));
        if (node != null) arr[idx++] = node;
        for(int i=block.reverseSchedule.size()-1;i>=0;i--) {
            arr[idx++] = block.reverseSchedule.get(i);
        }
        return arr;
    }

    private static Node findSingleCFGOut(Node node) {
        if (node instanceof StartNode) {
            for(var n:node._outputs) if (n instanceof ProjNode p && p._idx==0) return p;
            return null;
        }
        assert node._outputs.stream().filter(Node::isCFG).limit(2).count()<=1;
        for(var n:node._outputs) if(n.isCFG()) return n;
        return null;
    }

    private Block build(Node start) {
        var blocks = new IdentityHashMap<Node, Block>();
        var queue = new NodeQueue();
        queue.push(d(start));
        NodeData data;
        while((data=queue.pop())!=null) {
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
                    for(var out:if_._outputs) if (out.isCFG() && blocks.get(out) == null) queue.push(d(out));
                    break;
                }
                data = d(last);
                arr = appendNodes(arr, data.node, data.block);
            }
            blocks.put(first, switch(last) {
                case null -> new Block(arr, null, -1, new Block[0]);
                case IfNode i -> new Block(arr, i, -1, new Block[2]);
                case RegionNode r -> new Block(arr, r, Utils.find(r._inputs, prev), new Block[1]);
                case ReturnNode r -> new Block(arr, r, -1, new Block[0]);
                default -> throw new AssertionError("Unexpected block exit node");
            });
        }
        for (var block: blocks.values()) {
            switch(block.exit) {
                case null: break;
                case IfNode i:
                    for(var out:i._outputs) if (out instanceof ProjNode p) block.next[p._idx] = blocks.get(p);
                    break;
                case RegionNode r:
                    block.next[0] = blocks.get(r);
                    break;
                default:
                    assert block.exit instanceof ReturnNode;
            }
        }
        return blocks.get(start);
    }

    public static Scheduler.Block schedule(StartNode start) {
        var scheduler = new Scheduler();
        scheduler.doMarkAlive(start);
        scheduler.doBuildCTF(start);
        scheduler.doSchedule();
        return scheduler.build(start);
    }

}
