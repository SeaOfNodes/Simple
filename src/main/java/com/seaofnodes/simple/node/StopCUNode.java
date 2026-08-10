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
        // Memory start from all returns
        TypeMem xmem = TypeMem.START;
        // Escape summaries are monotone discovery facts.  In Opto, a Return
        // can widen from a precise set to FULL; the public-only normalization
        // below must not forget private bits already discovered precisely from
        // that Return on an earlier SCCP step.
        if( CodeGen.CODE._phase == CodeGen.Phase.Opto &&
            _type instanceof TypeTuple old && old.mem() instanceof TypeMem omem )
            xmem = xmem.makeFrom(XInt.meet(xmem._escFs,omem._escFs),
                                 XInt.meet(xmem._escAs,omem._escAs));

        for( Node def : _inputs ) {
            ReturnNode ret = (ReturnNode)def;
            if( ret._type == Type.TOP ) continue;
            if( !(ret._type instanceof TypeTuple tret) )
                return TypeTuple.STATE; // Must be Type.BOTTOM

            FunNode fun = ret.fun();
            boolean noUserCtor = fun.isInstance() &&
                !CodeGen.hasUserConstructor(((TypeMemPtr)fun.sig().arg(0))._obj);
            // Instance <init> is an allocation helper, never an externally
            // callable constructor when a user constructor exists.  Otherwise
            // the same no-arg <init> is the public constructor.
            boolean pub = fun.isPublic() && (fun.isClz() || noUserCtor);
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
                xmem = xmem.escapes(tret.ret());
                xmem = publicBottom(xmem);
                xmem = (TypeMem)xmem.meet(publicBottom(tret.mem(),xmem));
            }
        }
        assert xmem._alias==1 && !xmem._one;
        return TypeTuple.make(Type.CONTROL,xmem,Type.BOTTOM);
    }

    // FULL escape sets arise from global BOTTOM.  Across a compilation-unit
    // boundary global BOTTOM exposes every public field, while retaining any
    // private escapes already discovered precisely.
    private TypeMem publicBottom(TypeMem mem) { return publicBottom(mem,mem); }
    private TypeMem publicBottom(TypeMem mem, TypeMem prior) {
        // The pessimistic Iter pass naturally lifts transient FULL inputs to
        // precise finite sets.  Replacing FULL early there would let a later
        // input refinement add bits, reversing Iter's monotonic direction.
        // This normalization is needed only for Opto's optimistic grand cycle.
        if( CodeGen.CODE._phase != CodeGen.Phase.Opto ) return mem;
        int[] escFs = XInt.isHigh(mem._escFs)
            ? XInt.meet(prior._escFs,CodeGen.CODE.publicFIDXs())
            : mem._escFs;
        int[] escAs = XInt.isHigh(mem._escAs)
            ? XInt.meet(prior._escAs,CodeGen.CODE.publicAliases())
            : mem._escAs;
        return escFs==mem._escFs && escAs==mem._escAs ? mem : mem.makeFrom(escFs,escAs);
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
