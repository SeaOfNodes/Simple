package com.seaofnodes.simple.evaluator;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Objects;

public class Evaluator {

    private static final Object MEMORY = new Object();

    public record Obj(TypeStruct struct, Object[] fields) {
        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append("Obj<").append(struct._name).append("> {");
            for(int i=0; i<struct._fields.length; i++) {
                sb.append("\n  ").append(struct._fields[i]._fname).append("=").append(fields[i]);
            }
            return sb.append("\n}").toString();
        }
    }

    private static int getFieldIndex(TypeStruct struct, MemOpNode memop) {
        int idx = struct.find(memop._name);
        if( idx >= 0 ) return idx;
        throw new AssertionError("Field "+memop._name+" not found in struct " + struct._name);
    }

    public enum Status {
        TIMEOUT,
        FALLTHROUGH
    }

    /**
     * Find the start node from some node in the graph or null if there is no start node
     */
    private static StartNode findStart(BitSet visit, Node node) {
        if (node == null) return null;
        if( visit.get(node._nid) ) return null;
        visit.set(node._nid);
        StartNode start = node instanceof StartNode ? (StartNode) node : null;
        for( Node def : node._inputs ) {
            var res = findStart(visit,def);
            if( res != null ) start = res;
        }
        for( Node use : node._outputs ) {
            var res = findStart(visit,use);
            if( res != null ) start = res;
        }
        return start;
    }

    private final Object[] values;
    private final ArrayList<Object> phiCache = new ArrayList<>();
    private final StartNode start;
    private final Scheduler.Block startBlock;

    public Evaluator(Node graph) {
        var visited = new BitSet();
        this.start = findStart(visited, graph);
        if (this.start == null) {
            this.values = null;
            this.startBlock = null;
            return;
        }
        this.startBlock = Scheduler.schedule(this.start);
        this.values = new Object[visited.length()];
    }

    private long div(DivNode div) {
        long in2 = vall(div.in(2));
        return in2 == 0 ? 0 : vall(div.in(1)) / in2;
    }
    private double div(DivFNode div) {
        double in2 = vald(div.in(2));
        return in2 == 0 ? 0 : vald(div.in(1)) / in2;
    }

    private Object alloc(NewNode alloc) {
        var type = ((TypeMemPtr)alloc.compute())._obj;
        return new Obj(type, new Object[type._fields.length]);
    }

    private Object load(LoadNode load) {
        var from = (Obj)val(load.in(2));
        var idx = getFieldIndex(from.struct, load);
        return from.fields[idx];
    }

    private Object store(StoreNode store) {
        var to = (Obj)val(store.in(2));
        var val = val(store.in(3));
        var idx = getFieldIndex(to.struct, store);
        to.fields[idx] = val;
        return null;
    }

    private static boolean isTrue(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Number n) return n.longValue() != 0;
        return true;
    }

    private Object val(Node node) {
        return values[node._nid];
    }

    private long vall(Node node) {
        var v = val(node);
        if (v instanceof Number n) return n.longValue();
        throw new AssertionError("Not a long " + v);
    }
    private double vald(Node node) {
        var v = val(node);
        if (v instanceof Number n) return n.doubleValue();
        throw new AssertionError("Not a double " + v);
    }

    private Object cons(ConstantNode cons) {
        var type = cons.compute();
        if (type instanceof TypeInteger i) return i.value();
        if (type instanceof TypeFloat i) return i.value();
        assert type instanceof TypeMemPtr;
        return null;
    }

    private Object exec(Node node) {
        return switch (node) {
            case ConstantNode cons  -> cons(cons);
            case AddNode      add   -> vall(add.in(1)) + vall(add.in(2));
            case AddFNode     add   -> vald(add.in(1)) + vald(add.in(2));
            case BoolNode.EQ  eq    -> Objects.equals(val(eq.in(1)), val(eq.in(2))) ? 1L : 0L;
            case BoolNode.LE  le    -> vall(le.in(1)) <= vall(le.in(2)) ? 1L : 0L;
            case BoolNode.LT  lt    -> vall(lt.in(1)) <  vall(lt.in(2)) ? 1L : 0L;
            case BoolNode.EQF eq    -> Objects.equals(val(eq.in(1)), val(eq.in(2))) ? 1L : 0L;
            case BoolNode.LEF le    -> vald(le.in(1)) <= vald(le.in(2)) ? 1L : 0L;
            case BoolNode.LTF lt    -> vald(lt.in(1)) <  vald(lt.in(2)) ? 1L : 0L;
            case DivNode      div   -> div(div);
            case DivFNode     div   -> div(div);
            case MinusNode    minus -> -vall(minus.in(1));
            case MulNode      mul   -> vall(mul.in(1)) * vall(mul.in(2));
            case MulFNode     mul   -> vald(mul.in(1)) * vald(mul.in(2));
            case NotNode      not   -> isTrue(val(not.in(1))) ? 0L : 1L;
            case SubNode      sub   -> vall(sub.in(1)) - vall(sub.in(2));
            case SubFNode     sub   -> vald(sub.in(1)) - vald(sub.in(2));
            case CastNode     cast  -> val(cast.in(1));
            case ToFloatNode  cast  -> (double)vall(cast.in(1));
            case LoadNode     load  -> load(load);
            case StoreNode    store -> store(store);
            case NewNode      alloc -> alloc(alloc);
            case CProjNode    cproj -> ((Object[])val(cproj.ctrl()))[cproj._idx];
            case ProjNode     proj  -> ((Object[])val( proj.in(0) ))[ proj._idx];
            default                 -> throw new AssertionError("Unexpected node " + node);
        };
    }

    /**
     * Run the graph until either a return is found or the number of loop iterations are done.
     */
    public Object evaluate(long parameter, int loops) {
        if (start == null) return Status.TIMEOUT;
        var s = new Object[start.compute()._types.length];
        s[1] = parameter;
        for(int i=2;i<s.length;i++) s[i] = MEMORY;
        values[start._nid] = s;
        int i=0;
        Scheduler.Block block = this.startBlock;
        for(;;) {
            for(; i<block.nodes().length;i++) values[block.nodes()[i]._nid] = exec(block.nodes()[i]);
            i=0;
            switch (block.exit()) {
                case null:
                    return Status.FALLTHROUGH;
                case ReturnNode ret:
                    return val(ret.in(1));
                case IfNode ifn:
                    block = block.next()[isTrue(val(ifn.in(1))) ? 0 : 1];
                    if (block == null) return Status.FALLTHROUGH;
                    break;
                case RegionNode region:
                    if (loops-- <= 0) return Status.TIMEOUT;
                    int exit = block.exitId();
                    assert exit > 0 && region.nIns() > exit;
                    block = block.next()[0];
                    assert block != null;
                    for (; i < block.nodes().length; i++) {
                        if (!(block.nodes()[i] instanceof PhiNode)) break;
                        phiCache.add(val(block.nodes()[i].in(exit)));
                    }
                    for (i=0; i<phiCache.size(); i++) {
                        values[block.nodes()[i]._nid] = phiCache.get(i);
                    }
                    phiCache.clear();
                    break;
                default:
                    throw Utils.TODO("Unexpected control node " + block.exit());
            }
        }
    }

    public static Object evaluate(StopNode graph) {
        return evaluate(graph, 0);
    }

    public static Object evaluate(StopNode graph, long parameter) {
        return evaluate(graph, parameter, 1000);
    }

    public static Object evaluate(StopNode graph, long parameter, int loops) {
        var res = evaluateWithResult(graph, parameter, loops);
        if (res == Status.TIMEOUT) throw new RuntimeException("Timeout");
        return res;
    }

    public static Object evaluateWithResult(StopNode graph, long parameter, int loops) {
        return new Evaluator(graph).evaluate(parameter, loops);
    }

}
