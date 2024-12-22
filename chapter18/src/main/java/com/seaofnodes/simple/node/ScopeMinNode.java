package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;
import java.util.*;

import static com.seaofnodes.simple.Utils.TODO;

/* */
public class ScopeMinNode extends Node {

    /** The tracked fields are now complex enough to deserve a array-of-structs layout
     */
    public static class Var {
        public final int _idx;       // index in containing scope
        public final String _name;   // Declared name
        private Type _type;          // Declared type
        public final boolean _final; // Final field
        public Var(int idx, String name, Type type, boolean xfinal) {
            _idx = idx;
            _name = name;
            _type = type;
            _final = xfinal;
        }
        public Type type() {
            if( !_type.isFRef() ) return _type;
            // Update self to no longer use the forward ref type
            Type def = Parser.TYPES.get(((TypeMemPtr)_type)._obj._name);
            return (_type=_type.meet(def));
        }
        public Type lazyGLB() {
            Type t = type();
            return t instanceof TypeMemPtr ? t : t.glb();
        }
        @Override public String toString() {
            return _type.toString()+(_final ? " ": " !")+_name;
        }
    }

    public ScopeMinNode() { _type = TypeMem.BOT; }


    @Override public String label() { return "$mem"; }
    @Override public boolean isMem() { return true; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("MEM[ ");
        for( int j=2; j<nIns(); j++ ) {
            sb.append(j);
            sb.append(":");
            Node n = in(j);
            while( n instanceof ScopeNode loop ) {
                sb.append("Lazy_");
                n = loop.in(j);
            }
            if( n==null ) sb.append("___ ");
            else n._print0(sb, visited).append(" ");
        }
        sb.setLength(sb.length()-1);
        return sb.append("]");
    }


    @Override public Type compute() { return TypeMem.BOT; }

    @Override public Node idealize() { return null; }

    public Node in( Var v ) { return in(v._idx); }

    public Node alias( int alias ) {
        return in(alias<nIns() && in(alias)!=null ? alias : 1);
    }

    Node alias( int alias, Node st ) {
        while( alias >= nIns() ) addDef(null);
        return setDef(alias,st);
    }


    // Read or update from memory.
    // A shared implementation allows us to create lazy phis both during
    // lookups and updates; the lazy phi creation is part of chapter 8.
    Node _mem( int alias, Node st ) {
        // Memory projections are made lazily; if one does not exist
        // then it must be START.proj(1)
        Node old = alias(alias);
        if( old instanceof ScopeNode loop ) {
            ScopeMinNode loopmem = loop.mem();
            Node memdef = loopmem.alias(alias);
            // Lazy phi!
            old = memdef instanceof PhiNode phi && loop.ctrl()==phi.region()
                // Loop already has a real Phi, use it
                ? memdef
                // Set real Phi in the loop head
                // The phi takes its one input (no backedge yet) from a recursive
                // lookup, which might have insert a Phi in every loop nest.
                : loopmem.alias(alias, new PhiNode(Parser.memName(alias), TypeMem.BOT,loop.ctrl(),loopmem._mem(alias,null),null).peephole() );
            alias(alias,old);
        }
        // Memory projections are made lazily; expand as needed
        return st==null ? old : alias(alias,st); // Not lazy, so this is the answer
    }


    void _merge(ScopeMinNode that, RegionNode r) {
        int len = Math.max(nIns(),that.nIns());
        for( int i = 2; i < len; i++)
            if( alias(i) != that.alias(i) ) { // No need for redundant Phis
                // If we are in lazy phi mode we need to a lookup
                // by name as it will trigger a phi creation
                //Var v = _vars.at(i);
                Node lhs = this._mem(i,null);
                Node rhs = that._mem(i,null);
                alias(i, new PhiNode(Parser.memName(i), lhs._type.glb().meet(rhs._type.glb()), r, lhs, rhs).peephole());
            }
    }

    // Fill in the backedge of any inserted Phis
    void _endLoopMem( ScopeNode scope, ScopeMinNode back, ScopeMinNode exit ) {
        for( int i=2; i<back.nIns(); i++ ) {
            if( back.in(i) != scope ) {
                PhiNode phi = (PhiNode)in(i);
                assert phi.region()==scope.ctrl() && phi.in(2)==null;
                phi.setDef(2,back.in(i)); // Fill backedge
            }
            if( exit.alias(i) == scope ) // Replace a lazy-phi on the exit path also
                exit.alias(i,in(i));
        }
    }

    // Now one-time do a useless-phi removal
    void _useless( ) {
        for( int i=2; i<nIns(); i++ ) {
            if( in(i) instanceof PhiNode phi ) {
                // Do an eager useless-phi removal
                Node in = phi.peephole();
                IterPeeps.addAll(phi._outputs);
                phi.moveDepsToWorklist();
                if( in != phi ) {
                    if( !phi.iskeep() ) // Keeping phi around for parser elsewhere
                        phi.subsume(in);
                    setDef(i,in); // Set the update back into Scope
                }
            }
        }
    }

}
