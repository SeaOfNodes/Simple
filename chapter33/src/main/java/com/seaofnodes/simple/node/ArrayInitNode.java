package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;
import com.seaofnodes.simple.parser.Lexer;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Source checks for array construction and its yielded elements. Erased by
 * TypeCheck before serialization and scheduling. */
public final class ArrayInitNode extends Node {
    private final int _count; // List size, -1 for a generator, -2 for a yielded element.
    private final Lexer _loc;
    public ArrayInitNode(Node ctrl, Node mem, Node ptr, Node len, int count, Lexer loc, Ary<Node> values) {
        super(ctrl,mem,ptr,len);
        _count=count; _loc=loc;
        for( Node value : values ) addDef(value);
    }
    public ArrayInitNode(Node ctrl, Node value, Lexer loc) {
        super(ctrl,null,value);
        _count=-2; _loc=loc;
    }
    @Override public String label() { return "ArrayInit"; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p("arrayInit(").n(in(2)).p(")"); }
    @Override public Type compute() {
        if( in(0)._type != Type.CONTROL ) return Type.TOP;
        Type ptr = in(2)._type;
        if( _count < 0 ) return ptr;
        // A list is strict in all its inputs. Wait for every input before
        // choosing a constant payload or falling to the ordinary array type.
        // Otherwise one SCCP worklist order can return PtrTop (or an array)
        // before another unresolved input sends the result back up to Top.
        if( ptr.isHigh() || in(3)._type.isHigh() ) return Type.TOP;
        for( int i = 4; i < nIns(); i++ )
            if( in(i)._type.isHigh() ) return Type.TOP;
        if( !(ptr instanceof TypeMemPtr tmp) ) return ptr;
        Field body = tmp._obj.field("[]");
        if( body == null || !body._final || !(body._t instanceof TypeInteger storage) ) return ptr;
        if( !(in(3)._type instanceof TypeInteger length) || !length.isConstant() || length.value() != _count ) return ptr;
        long[] values = new long[_count];
        for( int i = 0; i < _count; i++ ) {
            Type value = in(i+4)._type;
            if( !(value instanceof TypeInteger integer) || !integer.isConstant() ) return ptr;
            values[i] = integer.value();
        }
        TypeConAry contents = TypeConAry.make(storage,values);
        TypeStruct array = TypeStruct.makeAry(tmp._obj._name,tmp._obj.field("#")._alias,body._alias,contents);
        // The surrounding ReadOnlyNode supplies the public view. Keep this
        // node's access flags consistent when its contents cease to be constant.
        return TypeMemPtr.make((byte)2,array,true).withAccess(tmp._ro);
    }
    @Override public Node idealize() { return null; }
    @Override public Parser.ParseException err() {
        if( in(0)._type==Type.XCONTROL ) return null;
        if( _count==-2 )
            return TypeMemPtr.mayBePartial(in(2)._type)
                ? Parser.error("Array initializer requires fully initialized elements",_loc) : null;
        if( !(in(3)._type instanceof TypeInteger len) )
            return Parser.error("Array length must be an integer",_loc);
        if( len.isConstant() && (len.value()<0 || len.value()>TypeInteger.U32._max) )
            return Parser.error("Array length is outside the u32 range",_loc);
        if( _count>=0 && (!len.isConstant() || len.value()!=_count) )
            return Parser.error("Array initializer has "+_count+" elements; length must equal that constant",_loc);
        for( int i=4; i<nIns(); i++ )
            if( TypeMemPtr.mayBePartial(in(i)._type) )
                return Parser.error("Array initializer requires fully initialized elements",_loc);
        return null;
    }
    @Override public boolean eq(Node n) { return this==n; }
}
