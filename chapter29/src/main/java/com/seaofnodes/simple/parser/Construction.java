package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.Iter;
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
 * References have just a yes/no restriction. Constructor self and ^ parameters
 * are incomplete. An allocation result is incomplete iff an actual argument is
 * incomplete. Copies and Phis preserve this restriction. Only self may be read
 * while incomplete; object-reference fields read from self conservatively remain
 * incomplete. Arrays are born initialized and cannot contain incomplete refs.
 * Ordinary calls and stores outside self require complete references.
 *
 * This permits cycles to be assembled but not traversed until the root new
 * returns. A new with only complete arguments, including null, returns complete.
 * A ^ formal is always restricted inside its constructor, even when a caller
 * passes a complete argument. No field requirements are inferred.
 *
 * Incompleteness lives in TypeMemPtr, not a parser provenance graph. Ordinary
 * Phis carry it around loops, conversions preserve it, and ConstructNode types
 * each allocation result from its actual arguments. Source obligations retain
 * their operands until the type fixed point, but hold no separate flow facts.
 *
 * The qualifier is a two-point lattice: complete meets incomplete to incomplete.
 * Constructor self and ^ formals seed incomplete; a ^ formal stays restricted
 * even if its known callers are complete. A ConstructNode wraps each source new
 * and ORs the actual argument qualifiers. Thus new Child(self) is incomplete,
 * while new Child(null) and new Child(oldParent) are complete, even though all
 * three calls share the same constructor Return. The raw allocator result is
 * complete after its own fields have been checked; ConstructNode adds the
 * caller's restriction and disappears after source checking.
 *
 * Reading a pointer field through a completed receiver gives a completed view
 * of that field too: its object's construction could only retain incomplete
 * references from the enclosing construction. There is no group ID to clear.
 * A read through incomplete self conservatively returns an incomplete reference
 * (except initialized arrays), even when the stored value happens to be complete.
 * Own-self access is identified by the receiver and its private memory, not by
 * the nominal class: a ^ argument of the same class cannot use that exception.
 *
 * Iter.types also solves qualifier cycles optimistically. For p = null followed
 * by p = new Link(p) in a loop, nothing introduces incompleteness, so the loop
 * stays complete. A backedge carrying self makes the Phi and its uses incomplete.
 * Both cases use the ordinary worklist; no recursive Load/Phi/Call query remains.
 */
final class Construction {
    private final Parser P;
    boolean _borrowed; // Source ^ parameters; separate compilation is still deferred.
    private final Ary<Node> _kept = new Ary<>(Node.class);
    private final Ary<Use> _uses = new Ary<>(Use.class);
    private final Ary<LoadNode> _reads = new Ary<>(LoadNode.class);
    Frame _frame;

    Construction(Parser P) { this.P = P; }

    static final class Frame {
        final boolean _owner;
        final Node _self;
        Frame(boolean owner, Node self) { _owner = owner; _self = self; }
    }
    private record Use(Node ctrl, Node value, String operation, Lexer loc) {}

    Frame enter(FunNode fun, boolean owner) {
        Frame old = _frame;
        _frame = new Frame(owner,fun.parm(2));
        return old;
    }

    private Node keep(Node n) { _kept.add(n.keep()); return n; }

    void complete(Node value, String operation, Lexer loc) {
        if( !_frame._owner ) return;
        _uses.add(new Use(keep(P.ctrl()),keep(value),operation,loc));
    }

    void ordinaryCall(Ary<Node> args, Lexer loc) {
        TypeFunPtr sig = args.last()._type instanceof TypeFunPtr tfp ? tfp : null;
        for( int i = 2; i<args.size()-1; i++ ) {
            if( i==2 && sig!=null && sig.nargs()>0 && sig.arg(0)==TypePtr.PTR ) continue;
            complete(args.at(i),"pass to an ordinary function",loc);
        }
    }

    Node memory(Node ptr, String field, Lexer loc) {
        if( own(ptr) ) return selfMem();
        complete(ptr,"read field '"+field+"'",loc);
        return P.mem();
    }

    Node loaded(LoadNode load) {
        if( _frame._owner ) _reads.add((LoadNode)keep(load));
        return P.peep(load);
    }

    void allocation(TypeFunPtr sig, Ary<Node> args, Lexer loc) {
        if( !_frame._owner ) return;
        for( int i = 0; i<args.size(); i++ )
            if( sig==null || i>=sig.nargs() || !(sig.arg(i) instanceof TypeMemPtr ptr && ptr._partial) )
                complete(args.at(i),"pass to a constructor parameter without '^'",loc);
    }

    Node selfMem() {
        Var mem = P._scope.lookup("#selfMem");
        return P._scope.in(P._scope.update(mem,null));
    }

    boolean own(Node ptr) {
        while( ptr instanceof ConvertNode || ptr instanceof ReadOnlyNode || ptr instanceof GuardNode ) ptr = ptr.in(1);
        return _frame._owner && ptr==_frame._self;
    }

    Node storeMemory(Node ptr) { return own(ptr) ? selfMem() : P.mem(); }

    void store(Node ptr, int alias, Node st, Node prior) {
        if( own(ptr) ) P._scope.update("#selfMem",P.mergeAlias(prior,alias,st));
        else P.storeMem(st,alias,prior);
    }

    void check() {
        Iter.types(P._code);
        for( LoadNode load : _reads )
            if( load.err()!=null ) throw load.err();
        for( Use use : _uses )
            if( !use.ctrl._type.isHigh() && TypeMemPtr.mayBePartial(use.value._type) )
                throw Parser.error("Cannot "+use.operation+" with an incomplete object",use.loc);
        P.structs.checkInitFields();
        for( Node n : _kept ) n.unkeep();
        _kept.clear();
        _uses.clear();
        _reads.clear();
    }

    void checkSerializable() {
        if( _borrowed )
            throw P.error("Separate compilation of constructors with '^' parameters is not supported yet");
    }
}
