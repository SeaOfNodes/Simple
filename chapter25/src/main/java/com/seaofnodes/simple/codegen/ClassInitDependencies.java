package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

/** Initialization requirements of the optimized, executable graph. */
public final class ClassInitDependencies {
    private final CodeGen code;
    private ArrayList<CompUnit> order;

    private static final class Effects {
        final FunNode fun;
        final TreeMap<String,String> touches = new TreeMap<>();
        final LinkedHashMap<Effects,String> callers = new LinkedHashMap<>();
        boolean complete;
        Effects(FunNode fun, boolean complete) { this.fun=fun; this.complete=complete; }
    }

    public ClassInitDependencies(CodeGen code) { this.code=code; }
    public void invalidate() { order=null; }

    // Resolve named class objects, including static nested class objects in a file.
    private CompUnit owner(String name) {
        name=name.substring(Parser.CLZ.length());
        CompUnit best=null;
        for( CompUnit cu : code._compunits.values() )
            if( cu._cname!=null && (name.equals(cu._cname) || name.startsWith(cu._cname+".")) &&
                (best==null || cu._cname.length()>best._cname.length()) ) best=cu;
        return best;
    }

    private static String reason(FunNode fun, String what, Parser.Lexer loc) {
        return fun._compunit._cname+" ("+fun.label()+"): "+what+
            (loc==null ? "" : " at "+loc.position());
    }

    // Walk control forwards, not backwards from Return: a live touch before an
    // infinite loop or nonreturning call must still contribute. Never cross a
    // function boundary through Call -> Fun or Return -> CallEnd graph edges.
    private void scan(Effects fx, TreeMap<Integer,Effects> functions) {
        ArrayDeque<CFGNode> work=new ArrayDeque<>();
        BitSet seen=new BitSet();
        work.add(fx.fun);
        while( !work.isEmpty() ) {
            CFGNode cfg=work.remove();
            if( seen.get(cfg._nid) || cfg._type.isHigh() ) continue;
            seen.set(cfg._nid);
            if( cfg instanceof CLInitNode init ) {
                if( init.ptr()._type instanceof TypeMemPtr ptr && ptr._obj!=TypeStruct.BOT && !ptr._obj.isHigh() ) {
                    if( Parser.startsClzPrefix(ptr._obj._name) ) {
                        CompUnit target=owner(ptr._obj._name);
                        if( target==null ) fx.complete=false;
                        else fx.touches.putIfAbsent(target._cname,reason(fx.fun,"field "+init._field,init._loc));
                    }
                } else fx.complete=false;
            }
            if( cfg instanceof CallNode call ) {
                String why=reason(fx.fun,"call",call._loc);
                if( call.fptr()._type instanceof TypeFunPtr fp && !XInt.isHigh(fp.fidxs()) && fp.nfcns()>0 ) {
                    for( int id=XInt.next(fp.fidxs(),0); id>=0; id=XInt.next(fp.fidxs(),id) ) {
                        Effects callee=functions.get(id);
                        if( callee==null ) { fx.complete=false; continue; }
                        if( callee.fun.isClz() )
                            fx.touches.putIfAbsent(callee.fun._compunit._cname,why);
                        else callee.callers.putIfAbsent(fx,why);
                    }
                } else fx.complete=false;
            }
            if( cfg instanceof ReturnNode ) continue;
            for( Node use : cfg._outputs ) {
                if( !(use instanceof CFGNode next) || next instanceof FunNode || next instanceof StopNode ) continue;
                if( next instanceof RegionNode r ) {
                    if( r._inputs.find(cfg)>0 ) work.add(r);
                } else if( next.in(0)==cfg ) work.add(next);
            }
        }
    }

    /** Reject post-Opto cycles; incomplete imports/native effects are NOT certified. */
    public void check() {
        if( order!=null ) return;
        assert code._phase.ordinal() >= CodeGen.Phase.Opto.ordinal();
        TreeMap<Integer,Effects> functions=new TreeMap<>();
        code._start.walk(n -> {
            if( n instanceof FunNode fun && !fun.isDead() && !fun._type.isHigh() )
                functions.put(fun.sig().fidx(),new Effects(fun,code.owns(fun)));
            return null;
        });
        for( Effects fx : functions.values() ) scan(fx,functions);

        // Recursive ordinary functions converge; only class requirements form
        // initialization edges. This uses final call targets, never old types.
        ArrayDeque<Effects> work=new ArrayDeque<>(functions.values());
        HashSet<Effects> queued=new HashSet<>(functions.values());
        while( !work.isEmpty() ) {
            Effects callee=work.remove();
            queued.remove(callee);
            for( var edge : callee.callers.entrySet() ) {
                Effects caller=edge.getKey();
                boolean changed=false;
                for( var touch : callee.touches.entrySet() )
                    if( caller.touches.putIfAbsent(touch.getKey(),edge.getValue()+"; then "+touch.getValue())==null ) changed=true;
                if( caller.complete && !callee.complete ) { caller.complete=false; changed=true; }
                if( changed && queued.add(caller) ) work.add(caller);
            }
        }
        TreeMap<String,CompUnit> units=new TreeMap<>();
        // Reuse local initializer summaries for diagnostics; no witnesses are
        // retained on CompUnit or on this checker after validation returns.
        IdentityHashMap<CompUnit,TreeMap<String,String>> reasons=new IdentityHashMap<>();
        for( CompUnit cu : code._compunits.values() )
            if( cu._cname!=null ) {
                units.put(cu._cname,cu);
                cu._classInitDeps=null;
                cu._initComplete=false; // Imported summaries arrive in checkpoint 2.
                if( cu._par!=null ) cu.addClassInitDep(code,cu._par);
            }
        for( Effects fx : functions.values() )
            if( fx.fun.isClz() ) {
                CompUnit cu=fx.fun._compunit;
                cu._initComplete=fx.complete;
                reasons.put(cu,fx.touches);
                for( String target : fx.touches.keySet() ) cu.addClassInitDep(code,units.get(target));
            }
        ArrayList<CompUnit> sorted=new ArrayList<>(), path=new ArrayList<>();
        IdentityHashMap<CompUnit,Integer> colors=new IdentityHashMap<>();
        for( CompUnit cu : units.values() ) visit(cu,colors,path,sorted,reasons);
        for( CompUnit cu : sorted )
            if( cu._classInitDeps!=null )
                for( CompUnit dep : cu._classInitDeps ) cu._initComplete &= dep._initComplete;
        order=sorted;
    }

    // Temporary checkpoint lowering: retain existing runtime behavior. Later
    // this becomes flag-and-call lowering, and obligations survive serialization.
    public void eraseChecks() {
        ArrayList<CLInitNode> checks=new ArrayList<>();
        code._start.walk(n -> { if( n instanceof CLInitNode init ) checks.add(init); return null; });
        for( CLInitNode init : checks )
            if( !init.isDead() ) init.subsume(init.in(0));
        code.invalidateIDepthCaches();
    }

    private void visit(CompUnit cu, IdentityHashMap<CompUnit,Integer> colors,
                       ArrayList<CompUnit> path, ArrayList<CompUnit> sorted,
                       IdentityHashMap<CompUnit,TreeMap<String,String>> reasons) {
        Integer color=colors.get(cu);
        if( color!=null ) {
            if( color==2 ) return;
            int start=path.indexOf(cu);
            StringBuilder message=new StringBuilder("Cyclic class initialization: ");
            for( int i=start; i<path.size(); i++ ) message.append(path.get(i)._cname).append(" -> ");
            message.append(cu._cname);
            for( int i=start; i<path.size(); i++ ) {
                CompUnit from=path.get(i), to=i+1<path.size() ? path.get(i+1) : cu;
                message.append("\n  ").append(from._cname).append(" -> ").append(to._cname)
                    .append(": ").append(from._par==to ? "parent of "+from._cname : reasons.get(from).get(to._cname));
            }
            throw Parser.error(message.toString(),null);
        }
        colors.put(cu,1);
        path.add(cu);
        ArrayList<CompUnit> deps=new ArrayList<>();
        if( cu._classInitDeps!=null ) for( CompUnit dep : cu._classInitDeps ) deps.add(dep);
        deps.sort(Comparator.comparing(dep -> dep._cname));
        for( CompUnit dep : deps ) visit(dep,colors,path,sorted,reasons);
        path.removeLast();
        colors.put(cu,2);
        sorted.add(cu);
    }

    /** Prerequisite-first order of the known graph, not a completeness certificate. */
    public ArrayList<CompUnit> order() { check(); return new ArrayList<>(order); }
}
