package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

// Sparse conditional constant propagation within each function.
abstract public class Opto {

    public static void opto(CodeGen code) {
        Ary<Type> oldTypes = new Ary<>(Type.class);
        WorkList<Node> deps = new WorkList<>();
        WorkList<Node> changes = new WorkList<>();
        code._start.walk(n -> {
            oldTypes.setX(n._nid,n._type);
            if( n instanceof CallEndNode cend ) cend.freezeResult();
            n._type = Type.TOP;
            code.add(n);
            // Calls retain their signature and whole-memory effects, but do
            // not pass optimistic values between functions in this chapter.
            if( n instanceof CallNode call ) call.unlink_all();
            return null;
        });
        // Keep each function's unknown-caller Start input. Its parameters
        // receive their declared types, independently of the actual calls.
        // The next chapter removes those inputs and discovers call edges.
        Node n;
        while( (n = code._iter._work.pop()) != null ) {
            if( n.isDead() ) continue;
            Type old = n._type, next = n.compute();
            if( next!=oldTypes.atX(n._nid) || n.isUnlocked() ) changes.push(n);
            if( old == next ) continue;
            assert old.isa(next); // Optimistic types only fall.
            assert next.isa(oldTypes.at(n._nid)) : n.getClass().getSimpleName()+"#"+n._nid+" old="+oldTypes.at(n._nid)+" next="+next;
            n._type = next;
            code.addAll(n._outputs);
            // SCCP consumes these without running peepholes. Save them for
            // the following Iter pass before moving clears the dependency list.
            for( int i=0; i<n.nDeps(); i++ ) deps.push(n.dep(i));
            n.moveDepsToWorklist(code._iter);
            assert check(code);
        }
        Node dep;
        while( (dep=deps.pop())!=null ) code.add(dep);
        moveChangesToWorklist(code, oldTypes, changes);
    }

    private static void moveChangesToWorklist(CodeGen code, Ary<Type> oldTypes, WorkList<Node> changes) {
        Node n;
        while( (n=changes.pop())!=null ) {
            if( n.isDead() ) continue;
            assert n.compute() == n._type; // Hit the fixed point.
            Type old = oldTypes.atX(n._nid);
            assert old==null || n._type.isa(old);
            if( n._type!=old || n.isUnlocked() ) {
                code.add(n);
                code.addAll(n._inputs);
                code.addAll(n._outputs);
                n.moveDepsToWorklist();
            }
        }
    }

    private static boolean check(CodeGen code) {
        code._start.walk(n -> {
            Type next = n.compute();
            assert next == n._type || (n._type.isa(next) && code._iter._work.on(n));
            return null;
        });
        return true;
    }
}
