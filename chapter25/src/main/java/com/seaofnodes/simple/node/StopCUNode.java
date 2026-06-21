package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

// Stop of a single compilation unit, distinct from the outer whole-program Stop.
public class StopCUNode extends StopNode {
    public StopCUNode() { super(); }
    public StopCUNode(StopNode stop) { super(stop); }
    @Override public Tag serialTag() { return Tag.StopCU; }

    private StartCUNode start() {
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

        for( Node def : _inputs ) {
            ReturnNode ret = (ReturnNode)def;
            if( ret._type == Type.TOP ) continue;
            if( !(ret._type instanceof TypeTuple tret) )
                return TypeTuple.STATE; // Must be Type.BOTTOM

            FunNode fun = ret.fun();
            boolean pub = fun.isPublic() && fun.isInit();
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

            // Does this function escape?  If not, then no need to MEET
            // here as its effects are not visible to the outside world.
            addDepForwards(start); // Check again if function escapes
            if( pub || start.escapedFIDX(sig.fidx()) ) {
                if( tret.ctl().isHigh() ) continue; // Return not reachable
                // Capture (precisely?) all escaping pointer aliases and fidxs.
                // Escaped fidxs means the linked world can call that function;
                // escaped aliases means the linked world can R/W those aliases.
                xmem = xmem.escapes(tret.ret());
                xmem = (TypeMem)xmem.meet(tret.mem());
            }
        }
        assert xmem._alias==1 && !xmem._one;
        return TypeTuple.make(Type.CONTROL,xmem,Type.BOTTOM);
    }

    static Node make( BAOS bais ) {
        StopCUNode stop = new StopCUNode();
        stop.setDefX(bais.packed1()-1,null);
        return stop;
    }
}
