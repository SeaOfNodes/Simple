package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;

// A collection of function ReturnNodes for a compilation unit.
public class StopNode extends CFGNode {

    public StopNode() {
        super();
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop); }
    @Override public Tag serialTag() { return Tag.Stop; }
    public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, AryInt aliases) { baos.packed1(nIns()); }
    static Node make( BAOS bais ) {
        StopNode stop = new StopNode();
        stop.setDefX(bais.packed1()-1,null);
        return stop;
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // For the sake of many old tests, and single value prints as "return val"
        ReturnNode ret1 = null;
        for( Node n : _inputs ) {
            if( n instanceof ReturnNode ret ) {
                if( ret1 != null ) { ret1=null; break; }
                ret1 = ret;
            }
        }
        if( ret1!=null ) return ret1._print0(sb,visited);

        sb.append("Stop[ ");
        for( Node n : _inputs )
            if( n instanceof ReturnNode ret ) {
                String name = ret.fun()._name;
                if( name== null || !name.startsWith("sys.") )
                    ret._print0(sb, visited).append(" ");
            }
        return sb.append("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        ReturnNode ret1 = null;
        for( Node n : _inputs ) {
            // TODO: remove isPublic
            if( n instanceof ReturnNode ret && ret.fun().isPublic() ) {
                if( ret1 != null ) return null; // Ambiguous
                ret1 = ret;
            }
        }
        return ret1;
    }

    @Override
    public TypeTuple compute() {
        // During Parsing, new Stops and Returns are being added.  This Stop
        // cannot know if more are coming, so must assume the worst.
        if( CodeGen.CODE._phase ==null || CodeGen.CODE._phase == CodeGen.Phase.Parse )
            return TypeTuple.STATE;
        StartNode start = CodeGen.CODE._start;

        // Just meet-over-inputs.  Due to the clunky double-stacked nature of
        // StopNodes, this is either a meet over Stops or a meet over Returns.
        TypeTuple tt = TypeTuple.STATE.dual();
        for( Node def : _inputs ) {
            if( def instanceof ReturnNode ret ) {
                // Dead Return
                if( ret._type == Type.TOP ) continue;
                if( !(ret._type instanceof TypeTuple tret) )
                    return TypeTuple.STATE; // Some broken thing


                // Does this function escape?  If not, then no need to MEET
                // here as its effects are not visible to the outside world.
                FunNode fun = ret.fun();
                addDep(start);
                if( !start.escapedFIDX(fun.sig().fidx()) &&
                    !(fun.isPublic() && fun.isInit()) )
                    continue;

                // Capture (precisely?) all escaping pointer aliases and fidxs.
                // Escaped fidxs means the linked world can call that function;
                // escaped aliases means the linked world can R/W those aliases.

                // Tuple meet the first 3 elements
                Type ctl = tt._types[0].meet(tret._types[0]);
                Type mem = tt._types[1].meet(tret._types[1]);
                Type val = tt._types[2].meet(tret._types[2]);

                if( mem instanceof TypeMem tmem ) {
                    // Gather escaping {aliases,functions} from the return expression
                    tmem = tmem.escapes(tret.ret());
                    // Returns for public <init> and <clinit> are alive because
                    // public, so force their FIDX to escape
                    if( fun.isPublic() && fun.isInit() ) {
                        tmem = tmem.escapes(fun.sig());
                        // Classes also escape their public clazz pointer,
                        // which then escapes all their public fields.  Private
                        // fields do not escape, and are only available from
                        // the local class code.
                        if( fun.isClz() )
                            tmem = tmem.escapes(fun.sig()._sig[0]);
                    }
                    mem = tmem;
                }
                tt = (TypeTuple)tt.meet(TypeTuple.make(ctl,mem,val));

            } else
                // Stacked StopNodes just MEET
                tt = (TypeTuple)tt.meet(def._type);
        }
        return tt;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( in(i) instanceof ReturnNode ret && addDep(ret.fun()).isDead() )
                delDef(i--);
        if( len != nIns() ) return this;
        return null;
    }

    @Override public int idepth() {
        if( _idepth!=0 ) return _idepth;
        int d=0;
        for( Node ret : _inputs )
            // All public results are hooked on the Stop, including data
            // values, so must check CFG
            if( ret instanceof CFGNode cfg )
                d = Math.max(d,cfg.idepth()+1);
        return _idepth=d;
    }
}
