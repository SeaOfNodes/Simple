package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import java.util.*;

/**
 *  Memory Merge - a merge of many aliases into a "fat memory".  All aliases
 *  are here, but most will be lazy - take the default fat memory.
 */
public class MemMergeNode extends Node {

    /*
     *  In-Progress means this is being used by the Parser to track memory
     *  aliases.  No optimizations are allowed.  When no longer "in progress"
     *  normal peeps work.
     */
    public final boolean _inProgress;

    public MemMergeNode( boolean inProgress) { _type = TypeMem.BOT; _inProgress = inProgress; }
    public MemMergeNode(MemMergeNode mem) { super(mem); _inProgress = false; }


    // If being used by a Scope, this is "in progress" from the Parser.
    // Otherwise, it's a memory merge
    boolean inProgress() { return _inProgress; }

    @Override public String label() { return "ALLMEM"; }
    @Override public boolean isMem() { return true; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append(_inProgress ? "Merge[" : "MEM[ ");
        for( int j=2; j<nIns(); j++ ) {
            sb.append(j);
            sb.append(":");
            Node n = in(j);
            while( n instanceof ScopeNode loop ) {
                sb.append("Lazy_");
                n = loop.mem(j);
            }
            if( n==null ) sb.append("___ ");
            else n._print0(sb, visited).append(" ");
        }
        sb.setLength(sb.length()-1);
        return sb.append("]");
    }

    // Make a memory merge: no longer a Scope really, tracking memory state but
    // not related to the parser in any way.
    public Node merge() {
        // Force default memory to not be lazy
        MemMergeNode merge = new MemMergeNode(false);
        for( Node n : _inputs )
            merge.addDef(n);
        merge._mem(1,null);
        return merge.peephole();
    }


    @Override public Type compute() { return TypeMem.BOT; }

    @Override public Node idealize() {
        if( inProgress() ) return null;

        // If not merging any memory (all memory is just the default)
        if( allDefault() )
            return in(1);       // Become default memory

        return null;
    }
    private boolean allDefault() {
        for( int i=2; i<nIns(); i++ )
            if( in(1) != in(i) )
                return false;
        return true;
    }


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
            MemMergeNode loopmem = loop.mem();
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


    void _merge( MemMergeNode that, RegionNode r) {
        int len = Math.max(nIns(),that.nIns());
        for( int i = 2; i < len; i++)
            if( alias(i) != that.alias(i) ) { // No need for redundant Phis
                // If we are in lazy phi mode we need to a lookup
                // by alias as it will trigger a phi creation
                Node lhs = this._mem(i,null);
                Node rhs = that._mem(i,null);
                alias(i, new PhiNode(Parser.memName(i), lhs._type.glb().meet(rhs._type.glb()), r, lhs, rhs).peephole());
            }
    }

    // Fill in the backedge of any inserted Phis
    void _endLoopMem( ScopeNode scope, MemMergeNode back, MemMergeNode exit ) {
        Node exit_def = exit.alias(1);
        for( int i=1; i<nIns(); i++ ) {
            if( in(i) instanceof PhiNode phi && phi.region()==scope.ctrl() ) {
                assert phi.in(2)==null;
                phi.setDef(2,back.in(i)==scope ? phi : back.in(i)); // Fill backedge
            }
            if( exit_def == scope ) // Replace a lazy-phi on the exit path also
                exit.alias(i,in(i));
        }
    }

    // Now one-time do a useless-phi removal
    void _useless( ) {
        for( int i=2; i<nIns(); i++ ) {
            if( in(i) instanceof PhiNode phi ) {
                // Do an eager useless-phi removal
                Node in = phi.peephole();
                CodeGen.CODE.addAll(phi._outputs);
                phi.moveDepsToWorklist();
                if( in != phi ) {
                    if( !phi.iskeep() ) // Keeping phi around for parser elsewhere
                        phi.subsume(in);
                    setDef(i,in); // Set the update back into Scope
                }
            }
        }
    }

    @Override public boolean eq( Node n ) {
        return this==n || !_inProgress;
    }
}
