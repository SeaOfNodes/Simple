package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

/** Shared declaration, assignment, and return conversions. */
final class Conversions {
    private final Parser P;

    Conversions(Parser P) { this.P = P; }

    // Widen ints to floats; narrow wide int types.
    // Early error if types do not match variable.
    Node convertExpr( Node expr, Type t ) {
        if( !expr._type.accessISA(t) )
            throw P.error("Type "+expr._type.str()+" is not of declared type "+t.str());
        if( t instanceof TypeMemPtr tmp && tmp.isFinal() )
            expr = P.peep(new ReadOnlyNode(expr));
        // A FunPtr carries the semantic edge to its function's Return.  Do not
        // hide that edge behind a Convert which can fold to a plain constant.
        if( expr instanceof FunPtrNode && t instanceof TypeFunPtr )
            return expr;
        // Auto-widen array to i64 (cast ptr to raw int bits)
        if( t == TypeInteger.BOT && expr._type instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            return P.peep(new AddNode(P.peep(new PtrToIntNode(expr)),ConFldOffNode.make(tmp._obj,"[]")));
        // Widen ints to floats, narrow wide types
        return P.peep(new ConvertNode(t,expr));
    }

    // Inferred `var` declarations describe writable slots, not the exact
    // value used to initialize them.  Explicitly typed locals retain the
    // user's nullability and numeric-width guarantees; `val` retains the
    // initializer's precise type.
    static Type mutableInferredType(Type t) {
        return switch( t ) {
        case TypeInteger ti -> TypeInteger.BOT;
        case TypeFloat   tf -> TypeFloat.F64;
        case TypeMemPtr tmp -> tmp.makeVar();
        case TypeFunPtr tfp -> tfp;
        case TypeConAry a -> a;
        case TypePtr     ptr -> TypePtr.PTR;
        case TypeScalar  ts -> TypeScalar.BOT;
        default             -> t;
        };
    }

    Type constructorFieldType(Type t) {
        if( t instanceof TypeMemPtr ptr && ptr._obj.isConAry() ) {
            TypeStruct declared = (TypeStruct)Parser.TYPES.get(ptr._obj._name);
            TypeStruct shape = P.types.typeAry(declared._fields[1]._t,ptr._obj._fields[1]._final);
            return TypeMemPtr.make((byte)2,shape,false).withAccess(ptr._ro);
        }
        return mutableInferredType(t);
    }

    // Seed an inferred return merge with the declared numeric family instead
    // of a transient constant/range.  Pointer nullability remains part of the
    // inferred function result and is combined by the later Phi.
    static Type inferredReturnType(Type t) {
        return switch( t ) {
        case TypeInteger ti -> TypeInteger.BOT;
        case TypeFloat   tf -> TypeFloat.F64;
        default             -> t;
        };
    }

    Node widenInt( Node expr, Type t ) {
        return (expr._type instanceof TypeInteger || expr._type==Type.NIL) && t instanceof TypeFloat
            ? P.peep(new ToFloatNode(expr)) : expr;
    }

}
