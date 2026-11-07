package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

import java.util.HashMap;
import java.util.IdentityHashMap;

// Stop of a single compilation unit, distinct from the outer whole-program Stop.
public class StopCUNode extends StopNode {
    public StopCUNode() { super(); }
    public StopCUNode(StopNode stop) { super(stop); }
    @Override public Tag serialTag() { return Tag.StopCU; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) { baos.packed1(nIns()); }
    static Node make( BAOS bais ) {
        StopCUNode stop = new StopCUNode();
        stop.setDefX(bais.packed1()-1,null);
        return stop;
    }

    // CompUnit owns this wrapper by identity. Even empty units must not share
    // a Stop: doing so can merge their Starts and their kept memory projections.
    @Override public boolean eq(Node n) { return false; }

    public StartCUNode start() {
        for( Node use : _outputs )
            if( use instanceof StartCUNode start )
                return start;
        return null;
    }

    @Override
    public TypeTuple compute() {
        // During Parsing, new Stops and Returns are being added.  This Stop
        // cannot know if more are coming, so must assume the worst.
        if( CodeGen.CODE._phase ==null || CodeGen.CODE._phase == CodeGen.Phase.Parse )
            return TypeTuple.STATE;

        StartCUNode start = start();
        // Recompute from inputs, not our previous result. Unknown values carry
        // FULL escape sets: restricting them to public names would lose private
        // objects/functions which a later load-forwarding rewrite can reveal.
        TypeMem xmem = TypeMem.START;

        for( Node def : _inputs ) {
            ReturnNode ret = (ReturnNode)def;
            if( ret._type == Type.TOP ) continue;
            if( !(ret._type instanceof TypeTuple tret) )
                return TypeTuple.STATE; // Must be Type.BOTTOM

            FunNode fun = ret.fun();
            boolean noUserCtor = fun.isInstance() &&
                !CodeGen.hasUserConstructor(((TypeMemPtr)fun.sig().arg(0))._obj);
            // Only entry initializers seed escape discovery. Constructors
            // and methods are discovered through escaping class/instance fields.
            boolean pub = fun.isRoot();
            TypeFunPtr sig = fun.sig();
            // If public init, the function escapes and the related class escapes
            if( pub ) {
                xmem = xmem.escapes(sig);
                // Classes also escape their public clazz pointer, which then
                // escapes all their public fields.  Private fields do not
                // escape, and are only available from the local class code.
                if( fun.isClz() )
                    xmem = xmem.escapesAliases(sig._sig[0]);
            }

            // The instance <init> Start edge supplies conservative pre-Opto
            // inputs, but its private-memory result is consumed only by the
            // allocation sequence and is never an externally visible return.
            if( fun.isInstance() && !noUserCtor ) continue;

            // Does this function escape?  If not, then no need to MEET
            // here as its effects are not visible to the outside world.
            addDepForwards(start); // Check again if function escapes
            if( pub || start.escapedFIDX(sig.fidx()) ) {
                if( tret.ctl().isHigh() ) continue; // Return not reachable
                // Capture (precisely?) all escaping pointer aliases and fidxs.
                // Escaped fidxs means the linked world can call that function;
                // escaped aliases means the linked world can R/W those aliases.
                xmem = xmem.escapesFrom(tret.ret(),start.outside(this));
                xmem = (TypeMem)xmem.meet(tret.mem());
            }
        }
        // All units share escapes from calls and unloaded bodies, including
        // paths with no Return. Revisit only when that shared result changes.
        GlobalEscapeNode global = CodeGen.CODE._globalEscapes;
        if( global!=null && !global.isDead() )
            xmem = xmem.escapes(addDepForwards(global)._type);
        assert xmem._alias==1 && !xmem._one;
        return TypeTuple.make(Type.CONTROL,xmem,Type.BOTTOM);
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
}
