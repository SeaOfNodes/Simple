package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.type.*;

/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    private final boolean _init; // Initializing writes are allowed to write null

    /**
     * @param name  The struct field we are assigning to
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base where we will store a value
     * @param off   The offset inside the struct base
     * @param value Value to be stored
     */
    public StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node mem, Node ptr, Node off, Node value, boolean init) {
        super(loc, name, alias, false, glb, mem, ptr, off, value);
        _init = init;
    }

    // Debugger label
    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && _init==((StoreNode)other)._init;
    }

    @Override public String  label() { return "st_"+mlabel(); }
    @Override public boolean isMem() { return true; }

    public Node val() { return in(4); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(".").p(_name).p("=").p( val()).p(";");
    }

    @Override
    public Type compute() {
        Type val = val()._type;
        if( _declaredType.isFRef() && val instanceof TypeMemPtr tmp && !tmp.isFRef() )
            _declaredType = tmp;
        Type t = val.join(_declaredType).meet(MemMergeNode.contents(mem(),_alias,this));
        return TypeMem.make(_alias,t);
    }

    @Override
    public Node idealize() {
        if( mem() instanceof MemMergeNode merge ) {
            setDef(1,CodeGen.CODE.add(merge.alias(_alias)));
            return this;
        }


        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() && _alias==st._alias &&  // Must check same object
            off()==st.off() &&  // And same offset
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) ) {
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        // Value is automatically truncated by narrow store
        if( val() instanceof AndNode and && and.in(2)._type.isConstant()  ) {
            int log = _declaredType.log_size();
            if( log<3 ) {       // And-mask vs narrow store
                long mask = ((TypeInteger)and.in(2)._type).value();
                long bits = (1L<<(8<<log))-1;
                // Mask does not mask any of the stored bits
                if( (bits&mask)==bits )
                    // So and-mask is already covered by the store
                    { setDef(4,and.in(1)); return this; }
            }
        }

        // Store will chop high order bits off; math to change those bits can be dropped.
        if( val() instanceof SarNode shr &&
            shr.in(1) instanceof ShlNode shl &&
            shr.in(2)._type.isConstant() &&
            shl.in(2)._type.isConstant() ) {
            TypeInteger shrC = (TypeInteger) shr.in(2)._type;

            // size of the thing that sign-extends
            int base_size = (1 << shr.in(1)._type.log_size()) << 3;
            int not_affected_bits = base_size - (int) shrC.value();
            int store_size = (1 << log_size()) << 3;
            // if the store is unrelated to the shift amount, then get rid of the shift
            if( shl.in(2)._type == shr.in(2)._type && shrC.value() >= store_size && not_affected_bits >= store_size) {
                setDef(4, shl.in(1));
                return this;
            }
        }
        return null;
    }

    // Phi(mem, Store(phi,ptr,off,val)) -> Store(mem,ptr,off,Phi(load(mem),val)).
    // With no observers of the backedge store, only the last value matters.
    // The entry load preserves the field even when the loop takes zero trips.
    Node sink(MemPhiNode phi) {
        phi.addDepForwards(this);
        if( nOuts()!=1 ) return null;
        phi.addDep(ptr());
        // Start with a fixed field of an allocation dominating the loop.
        // In particular, do not sink stores through a loop-varying address.
        if( !(ptr() instanceof ProjNode p) || !(p.in(0) instanceof NewNode obj) ||
            !(ptr()._type instanceof TypeMemPtr tmp) || tmp._obj.isAry() ||
            !(off() instanceof ConstantNode) || err()!=null ) return null;
        LoopNode loop = (LoopNode)phi.region();
        // Bulk splitting identifies precise slices by Region/alias. Finish it
        // before replacing this memory point with a Store.
        for( Node use : loop._outputs )
            if( use instanceof BulkMemPhiNode ) {
                phi.addDepForwards(use);
                return null;
            }
        phi.addDep(obj);
        CFGNode ctrl = loop.entry();
        while( ctrl!=null && ctrl!=obj.cfg0() ) {
            phi.addDep(ctrl);
            ctrl=ctrl.idom(phi);
        }
        if( ctrl==null ) return null;

        Node mem = phi.in(1);
        Node init = new LoadNode(_loc,_name,_alias,_declaredType,mem,ptr(),off()).peephole();
        Node value = CodeGen.CODE.add(new PhiNode(_name,_declaredType,loop,init,val()).peephole());
        Node sink = new StoreNode(_loc,_name,_alias,_declaredType,mem,ptr(),off(),value,_init);
        // Break the memory cycle, killing the old store. Keep the Phi alive
        // even if that store was its last use, until our caller replaces it.
        phi.keep();
        phi.setDef(2,mem);
        phi.unkeep();
        return sink;
    }

    // Check that "mem" has no uses except "this"
    private boolean checkOnlyUse(Node mem) {
        if( mem.nOuts()==1 ) return true;
        // Add deps on the other uses (can be e.g. ScopeNode mid-parse) so that
        // when the other uses go away we can retry.
        for( Node use : mem._outputs )
            if( use != this )
                addDep(use);
        return false;
    }

    @Override
    public Parser.ParseException err() {
        Parser.ParseException err = super.err();
        if( err != null ) return err;
        if( ptr()._type == Type.TOP )
            return null; // Dead store
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        if( (tmp._ro || tmp._obj.field(_name)._final) && !_init )
            return Parser.error("Cannot modify final field '"+_name+"'",_loc);
        return null;
    }
}
