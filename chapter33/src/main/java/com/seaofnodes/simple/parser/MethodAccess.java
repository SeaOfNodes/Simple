package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Infer the access required through an instance method's receiver. */
final class MethodAccess {
    private final FunNode _fun;
    private final Set<Node> _aliases = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MethodAccess> _calls = new HashSet<>();
    private boolean _writes;

    private MethodAccess(FunNode fun) { _fun = fun; }

    static void infer(Ary<FunNode> functions) {
        HashMap<Integer,MethodAccess> methods = new HashMap<>();
        for( FunNode fun : functions ) methods.put(fun.sig().fidx(),new MethodAccess(fun));
        for( MethodAccess method : methods.values() ) method.scan(methods);
        boolean changed;
        do {
            changed = false;
            for( MethodAccess method : methods.values() )
                if( !method._writes )
                    for( MethodAccess callee : method._calls )
                        if( callee._writes ) { method._writes = true; changed = true; break; }
        } while( changed );
        for( MethodAccess method : methods.values() ) method.finish();
    }

    private void scan(HashMap<Integer,MethodAccess> methods) {
        if( _fun.parm(2) == null ) return; // Unused receiver.
        ArrayDeque<Node> work = new ArrayDeque<>();
        work.add(_fun.parm(2));
        while( !work.isEmpty() ) {
            Node value = work.remove();
            if( !_aliases.add(value) ) continue;
            for( Node use : value._outputs ) {
                if( use == null || use instanceof ReadOnlyNode ) continue;
                if( use instanceof StoreNode store ) {
                    if( store.ptr() == value || store.val() == value ) _writes = true;
                } else if( use instanceof CallNode call ) {
                    for( int i = 2; i < call.nIns()-1; i++ )
                        if( call.in(i) == value ) call(call,i-2,methods);
                } else if( use instanceof ReturnNode ret ) {
                    // Returning a writable alias must not grant write access
                    // to an object reached through a read-only receiver.
                    if( ret.expr() == value ) _writes = true;
                } else if( use instanceof LoadNode load && load.ptr() == value ) {
                    Type type = type(load);
                    if( type instanceof TypeMemPtr ptr ) {
                        if( !ptr._ro ) work.add(load);
                    } else if( type == Type.BOTTOM || type == TypeScalar.BOT ) _writes = true;
                } else if( use instanceof PhiNode || use instanceof GuardNode ) {
                    work.add(use);
                } else if( use instanceof ConvertNode convert ) {
                    if( !(convert.dst() instanceof TypeMemPtr ptr && ptr._ro) ) _writes = true;
                } else if( use instanceof BoolNode || use instanceof NotNode ||
                           use instanceof FldOffNode || use instanceof ScopeNode || use instanceof CLInitNode ) {
                    // Comparisons and field offsets do not expose write access.
                } else {
                    // Includes raw-address casts and unrecognized escapes.
                    _writes = true;
                }
            }
        }
    }

    private void call(CallNode call, int arg, HashMap<Integer,MethodAccess> methods) {
        // Every member of this struct is now declared. An unresolved bare
        // function name belongs to an outer scope, whose hidden slot is not self.
        if( arg == 0 && call.fptr() instanceof FRefNode ) return;
        Type type = type(call.fptr());
        if( !(type instanceof TypeFunPtr sig) || arg >= sig.nargs() ) { _writes = true; return; }
        if( arg == 0 && sig.arg(0) == TypePtr.PTR ) return;
        if( arg == 0 ) {
            if( XInt.isHigh(sig.fidxs()) ) { _writes = true; return; }
            for( int fidx = XInt.next(sig.fidxs(),-1); fidx >= 0; fidx = XInt.next(sig.fidxs(),fidx) ) {
                MethodAccess local = methods.get(fidx);
                if( local != null ) _calls.add(local);
                else {
                    FunNode target = CodeGen.CODE.link(fidx);
                    Type receiver = target == null ? sig.arg(0) : target.sig().arg(0);
                    if( !(receiver instanceof TypeMemPtr ptr && ptr._ro) ) _writes = true;
                }
            }
            return;
        }
        if( !(sig.arg(arg) instanceof TypeMemPtr ptr && ptr._ro) ) _writes = true;
    }

    private static Type type(Node value) {
        // Flow types can still be BOTTOM while the parser discovers fields.
        if( value instanceof LoadNode load ) {
            TypeStruct obj = load.off() instanceof ConFldOffNode off ? off._ts :
                type(load.ptr()) instanceof TypeMemPtr ptr ? ptr._obj : null;
            if( obj != null ) {
                if( !obj.isAry() && Parser.TYPES.get(obj._name) instanceof TypeStruct latest ) obj = latest;
                Field field = obj.field(load._name);
                if( field != null ) return field._t;
            }
        }
        return value._type;
    }

    private void finish() {
        ParmNode self = _fun.parm(2);
        if( self == null ) return;
        TypeMemPtr receiver = ((TypeMemPtr)self._declaredType).withAccess(!_writes);
        _fun.setSig(_fun.sig().makeFrom(receiver,0));
        if( _writes ) return;
        self._declaredType = receiver;
        self.setDef(1,ConstantNode.seed(receiver).peephole());
        // These parser-time assumptions are settled before Iter/SCCP. No
        // writable alias escapes a method classified as read-only.
        for( Node alias : _aliases )
            if( alias._type instanceof TypeMemPtr ptr ) {
                alias._type = ptr.makeRO();
                CodeGen.CODE.add(alias);
                CodeGen.CODE.addAll(alias._outputs);
            }
    }
}
