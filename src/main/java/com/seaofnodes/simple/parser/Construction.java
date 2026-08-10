package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/**
 * Field initialization is ordinary forward typing: New supplies Scalar.BOT
 * fields in private memory, Store replaces a field, and Phi meets the incoming
 * memories. Scalar.BOT survives a missing live arm; Integer.BOT describes an
 * initialized integer. Loads and merged Returns inspect these types after the
 * parser closes loops and compute() stabilizes. No backwards initialization walk.
 *
 * Constructor self and ^ parameters are incomplete. InitViewNode combines self
 * with its current private memory: set fields carry their declared types and
 * missing fields carry Scalar.BOT. Local ^ parameters meet these shapes through
 * ordinary forward flow; unknown callers promise no initialized fields. Only
 * the original constructor may write through an incomplete pointer.
 *
 * Calls borrowing self receive its Stores on the ordinary public memory edge,
 * using aliasable Escape slices without publishing its identity. Field values
 * come from memory, not the pointer's initialization shape. Incomplete values
 * may be retained only in private construction memory. Nested object references
 * conservatively promise no initialized fields; arrays are born initialized.
 * No field requirements are inferred and no extra memory arguments are added.
 *
 * Incompleteness lives in TypeMemPtr, not a parser provenance graph. Ordinary
 * Phis carry it around loops, conversions preserve it, and ConstructNode types
 * each allocation result from its actual arguments. Errors belong to the IR
 * nodes and are checked at TypeCheck; the parser keeps no source-use lists.
 *
 * The qualifier is a two-point lattice: complete meets incomplete to incomplete.
 * Constructor self and ^ formals seed incomplete; a ^ formal stays restricted
 * even if its known callers are complete. A ConstructNode wraps each source new
 * and ORs the actual argument qualifiers. Thus new Child(self) is incomplete,
 * while new Child(null) and new Child(oldParent) are complete, even though all
 * three calls share the same constructor Return. The raw allocator result is
 * complete after its own fields have been checked; ConstructNode adds the
 * caller's restriction and disappears after TypeCheck.
 *
 * Reading a pointer field through a completed receiver gives a completed view
 * of that field too: its object's construction could only retain incomplete
 * references from the enclosing construction. There is no group ID to clear.
 * A read through an incomplete pointer conservatively returns an incomplete reference
 * (except initialized arrays), even when the stored value happens to be complete.
 * Own-self access is identified by the receiver and its private memory, not by
 * the nominal class: a ^ argument of the same class cannot use that exception.
 *
 * The existing SCCP pass solves qualifier cycles optimistically. For p = null followed
 * by p = new Link(p) in a loop, nothing introduces incompleteness, so the loop
 * stays complete. A backedge carrying self makes the Phi and its uses incomplete.
 * Both cases use the ordinary worklist; no recursive Load/Phi/Call query remains.
 * A partial-only ConstructNode supplies the body view of self and ^ formals.
 * The Parm itself merges callers normally; inlining cannot erase the view.
 * Hidden helpers have only allocator callers, while the public constructor
 * binding denotes the allocator. Private-memory Parms therefore need no
 * fictitious external default or constructor-specific transfer function.
 * InitView and Construct are erased by the existing TypeCheck walk. There is
 * no extra construction pass; see docs/construction-groups.md.
 */
final class Construction {
    private final Parser P;
    boolean _borrowed; // Source ^ parameters; separate compilation is still deferred.
    Frame _frame;

    Construction(Parser P) { this.P = P; }

    static final class Frame {
        final boolean _owner;
        final Node _self;
        Frame(boolean owner, Node self) { _owner = owner; _self = self; }
    }

    Frame enter(FunNode fun, boolean owner) {
        Frame old = _frame;
        _frame = new Frame(owner,fun.parm(2));
        return old;
    }

    Node memory(Node ptr) {
        return own(ptr) ? selfMem() : P.mem();
    }

    Node selfMem() {
        Var mem = P._scope.lookup("#selfMem");
        return P._scope.in(P._scope.update(mem,null));
    }

    Node view(Node ptr) {
        return own(ptr) ? new InitViewNode((TypeMemPtr)((ParmNode)_frame._self)._declaredType,ptr,selfMem()).peephole() : ptr;
    }

    // Carry the constructor's Stores on the call's ordinary memory edge.
    // The borrowed receiver is read-only; this does not publish its identity.
    Node callMemory(Ary<Node> args, Node pub) {
        if( !_frame._owner ) return pub;
        boolean lends = false;
        for( int i=2; i<args.size()-1; i++ ) lends |= own(args.at(i));
        if( !lends ) return pub;
        Node self = _frame._self;
        TypeStruct ts = (TypeStruct)Parser.TYPES.get(((TypeMemPtr)self._type)._obj._name);
        Node priv = selfMem().keep();
        for( Field fld : ts._fields ) {
            pub.keep();
            Node loan = new EscapeNode(fld,self,priv,pub,true).peephole();
            Node next = P.mergeAlias(pub,fld._alias,loan);
            pub.unkeep();
            pub = next;
        }
        priv.unkeep();
        return pub;
    }

    boolean own(Node ptr) {
        while( ptr instanceof InitViewNode || ptr instanceof ConvertNode || ptr instanceof ReadOnlyNode || ptr instanceof GuardNode || (ptr instanceof ConstructNode con && con._partial) ) ptr = ptr.in(1);
        return _frame._owner && ptr==_frame._self;
    }

    Node storeMemory(Node ptr) { return own(ptr) ? selfMem() : P.mem(); }

    void store(Node ptr, int alias, Node st, Node prior) {
        if( own(ptr) ) P._scope.update("#selfMem",P.mergeAlias(prior,alias,st));
        else P.storeMem(st,alias,prior);
    }

    void checkSerializable() {
        if( _borrowed )
            throw P.error("Separate compilation of functions with '^' parameters is not supported yet");
    }
}
