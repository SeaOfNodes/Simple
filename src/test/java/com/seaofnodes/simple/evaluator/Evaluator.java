package com.seaofnodes.simple.evaluator;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;
import java.util.*;

public class Evaluator {

    private static final Object MEMORY = new Object();

    public record Obj(TypeStruct struct, Object[] fields) {
        private void init(IdentityHashMap<Obj, Integer> objs) {
            var oid = objs.get(this);
            if (oid != null) {
                if (oid != 0) objs.put(this, 0);
                return;
            }
            objs.put(this, -1);
            for (var obj : fields) {
                if (obj instanceof Obj o) o.init(objs);
            }
        }
        private static int p(StringBuilder sb, IdentityHashMap<Obj, Integer> objs, int id, String indentation, String step, String sep, Object obj) {
            if (obj instanceof Obj o) return o.p(sb, objs, id, indentation, step, sep);
            sb.append(obj);
            return id;
        }
        private int p(StringBuilder sb, IdentityHashMap<Obj, Integer> objs, int id, String indentation, String step, String sep) {
            var cid = objs.get(this);
            assert cid != null;
            if (cid > 0) {
                sb.append("obj@").append(cid);
                return id;
            }
            if (struct._name.equals("[u8]")) {
                sb.append('"');
                for (int i=struct._fields.length-1; i<fields.length; i++) {
                    var v = fields[i];
                    if (v == null) v = 0;
                    assert v instanceof Number;
                    var n = ((Number)v).byteValue() & 0xFF;
                    if (n >= 0x20 && n < 0x80) {
                        if (n == 0x22 || n == 0x5C) sb.append("\\");
                        sb.append((char)n);
                    } else {
                        sb.append("\\x").append("0123456789abcdef".charAt(n >> 4)).append("0123456789abcdef".charAt(n & 0xF));
                    }
                }
                sb.append('"');
                return id;
            }
            sb.append("Obj<").append(struct._name).append(">");
            if (cid == 0) {
                cid = id++;
                objs.put(this, cid);
                sb.append("@").append(cid);
            }
            sb.append("{");
            if (struct._fields.length == 0) {
                assert !struct.isAry();
                sb.append("}");
                return id;
            }
            String nextIndent = indentation + step;
            int e = struct._fields.length - 1;
            for(int i=0; i<e; i++) {
                sb.append(nextIndent).append(struct._fields[i]._fname).append("=");
                id = p(sb, objs, id, nextIndent, step, sep, fields[i]);
                sb.append(sep);
            }
            sb.append(nextIndent).append(struct._fields[e]._fname).append("=");
            if (struct.isAry()) {
                sb.append("[");
                if (fields.length > e) {
                    String innerIndent = nextIndent + step;
                    sb.append(innerIndent);
                    id = p(sb, objs, id, nextIndent, step, sep, fields[e]);
                    for (int i = e+1; i < fields.length; i++) {
                        sb.append(sep).append(innerIndent);
                        id = p(sb, objs, id, nextIndent, step, sep, fields[i]);
                    }
                    sb.append(nextIndent);
                }
                sb.append("]");
            } else {
                id = p(sb, objs, id, nextIndent, step, sep, fields[e]);
            }
            sb.append(indentation).append("}");
            return id;
        }
        private StringBuilder p(StringBuilder sb, String indentation, String step, String sep) {
            var objs = new IdentityHashMap<Obj, Integer>();
            init(objs);
            p(sb, objs, 1, indentation, step, sep);
            return sb;
        }
        @Override
        public String toString() {
            if (struct._name.equals("[u8]")) {
                var sb = new StringBuilder();
                for (int i=struct._fields.length-1; i<fields.length; i++) {
                    var v = fields[i];
                    if (v == null) v = 0;
                    assert v instanceof Number;
                    var n = ((Number)v).byteValue() & 0xFF;
                    sb.append((char)n);
                }
                return sb.toString();
            }
            return p(new StringBuilder(), "", "", ",").toString();
        }
        public String pretty() {
            return p(new StringBuilder(), "\n", "  ", "").toString();
        }
    }

    private static int getFieldIndex(TypeStruct struct, MemOpNode memop, long off) {
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

    private static long offToIdx(long off, TypeStruct t) {
        off -= t.aryBase();
        int scale = t.aryScale();
        long mask = (1L << scale)-1;
        assert (off & mask) == 0;
        return off>>scale;
    }

    private Object alloc(NewNode alloc) {
        TypeStruct type = alloc._ptr._obj;
        Object[] body=null;
        int num;
        if( type.isAry() ) {
            long sz = (Long)val(alloc.in(1));
            long n = offToIdx(sz, type);
            if( n < 0 )
                throw new NegativeArraySizeException(""+n);
            body = new Object[(int)n+1]; // Array body
            var elem = type._fields[1]._t;
            if (elem instanceof TypeInteger) {
                for (int i=0; i<n; i++) body[i+1] = 0L;
            } else if (elem instanceof TypeFloat) {
                for (int i = 0; i < n; i++) body[i+1] = 0D;
            } else {
                assert elem instanceof TypeMemPtr;
            }
            // Length value
            body[0] = vall(alloc.in(2+2));
        } else {
            body = new Object[num = type._fields.length];
            for( int i=0; i<num; i++ )
                body[i] = switch( alloc._ptr._obj._fields[i]._t ) {
                case TypeInteger ti -> 0L;
                case TypeFloat tf -> 0;
                default -> null;
                };
        }
        Object[] mems = new Object[type._fields.length+2];
        // mems[0] is control
        mems[1] = new Obj(type,body); // the ref
        // mems[2+...] are memory aliases
        return mems;
    }

    private Object load(LoadNode load) {
        var from = (Obj)val(load.ptr());
        var off = vall(load.off());
        var idx = getFieldIndex(from.struct, load, (Long)off);
        if( idx==from.struct._fields.length-1 && from.struct.isAry() ) {
            long len = from.fields.length - from.struct._fields.length + 1;
            long i = offToIdx(off, from.struct);
            if( i < 0 || i >= len )
                throw new ArrayIndexOutOfBoundsException("Array index out of bounds " + i + " < " + len);
            return from.fields[(int)i+from.struct._fields.length-1];

        } else
            return from.fields[idx];
    }

    private Object store(StoreNode store) {
        var to = (Obj)val(store.ptr());
        var off = vall(store.off());
        var val = val(store.val());
        var idx = getFieldIndex(to.struct, store, (Long)off);
        if( idx==to.struct._fields.length-1 && to.struct.isAry() ) {
            long len = to.fields.length - to.struct._fields.length + 1;
            long i = offToIdx(off, to.struct);
            if( i < 0 || i >= len )
                throw new RuntimeException("Array index out of bounds " + off + " <= " + len);
            to.fields[(int)i+to.struct._fields.length-1] = val;
        } else
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
        if( v == null ) return 0;
        throw new AssertionError("Not a double " + v);
    }

    private Object cons(ConstantNode cons) {
        var type = cons.compute();
        if (type instanceof TypeInteger i) return i.isConstant() ? i.value() : Integer.MAX_VALUE;
        if (type instanceof TypeFloat i) return i.value();
        return null;
    }

    private Object exec(Node node) {
        return switch (node) {
            case ConstantNode cons  -> cons(cons);
            case AddNode      add   -> vall(add.in(1)) + vall(add.in(2));
            case AddFNode     add   -> vald(add.in(1)) + vald(add.in(2));
            case BoolNode.EQF eq    -> vald(eq.in(1)) == vald(eq.in(2)) ? 1L : 0L;
            case BoolNode.LEF le    -> vald(le.in(1)) <= vald(le.in(2)) ? 1L : 0L;
            case BoolNode.LTF lt    -> vald(lt.in(1)) <  vald(lt.in(2)) ? 1L : 0L;
            case BoolNode.EQ  eq    -> Objects.equals(val(eq.in(1)), val(eq.in(2))) ? 1L : 0L;
            case BoolNode.LE  le    -> vall(le.in(1)) <= vall(le.in(2)) ? 1L : 0L;
            case BoolNode.LT  lt    -> vall(lt.in(1)) <  vall(lt.in(2)) ? 1L : 0L;
            case DivNode      div   -> div(div);
            case DivFNode     div   -> div(div);
            case MinusNode    minus -> -vall(minus.in(1));
            case MinusFNode   minus -> -vald(minus.in(1));
            case MulNode      mul   -> vall(mul.in(1)) * vall(mul.in(2));
            case MulFNode     mul   -> vald(mul.in(1)) * vald(mul.in(2));
            case NotNode      not   -> isTrue(val(not.in(1))) ? 0L : 1L;
            case SubNode      sub   -> vall(sub.in(1)) - vall(sub.in(2));
            case SubFNode     sub   -> vald(sub.in(1)) - vald(sub.in(2));
            case ShlNode      shl   -> vall(shl.in(1)) << vall(shl.in(2));
            case ShrNode      shr   -> vall(shr.in(1)) >>> vall(shr.in(2));
            case SarNode      sar   -> vall(sar.in(1)) >> vall(sar.in(2));
            case AndNode      and   -> vall(and.in(1)) & vall(and.in(2));
            case  OrNode      or    -> vall(or .in(1)) | vall(or .in(2));
            case XorNode      xor   -> vall(xor.in(1)) ^ vall(xor.in(2));
            case CastNode     cast  -> val(cast.in(1));
            case ToFloatNode  cast  -> (double)vall(cast.in(1));
            case LoadNode     load  -> load(load);
            case StoreNode    store -> store(store);
            case NewNode      alloc -> alloc(alloc);
            case CProjNode    cproj -> ((Object[])val(cproj.ctrl()))[cproj._idx];
            case ProjNode     proj  -> ((Object[])val( proj.in(0) ))[ proj._idx];
            case MemMergeNode mem   -> null;
            case ReadOnlyNode ro    -> val(ro.in(1));
            case CallNode     call  -> Utils.TODO(); // should not reach here, go the IfNode route
            default                 -> throw new AssertionError("Unexpected node " + node);
        };
    }

    /**
     * Run the graph until either a return is found or the number of loop iterations are done.
     */
    public Object evaluate(long parameter, int loops) {
        if (start == null) return Status.TIMEOUT;
        var s = new Object[start.compute()._types.length];
        s[1] = MEMORY;
        s[2] = parameter;
        values[start._nid] = s;
        int i=0;
        Scheduler.Block block = this.startBlock;
        for(;;) {
            for(; i<block.nodes().length;i++) values[block.nodes()[i]._nid] = exec(block.nodes()[i]);
            i=0;
            switch (block.exit()) {
            case null:
                return Status.FALLTHROUGH;
            case ReturnNode ret :  return val(ret .expr());
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
                    if (!(block.nodes()[i] instanceof PhiNode phi)) break;
                    var val = region instanceof FunNode fun && "main".equals(fun._name) && ((ParmNode)phi)._idx==2 && exit==1
                            ? parameter
                            : val(phi.in(exit));
                    phiCache.add(val);
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
