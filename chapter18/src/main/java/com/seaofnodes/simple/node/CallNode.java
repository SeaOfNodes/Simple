package com.seaofnodes.simple.node;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.util.BitSet;

/**
 *  Call
 */
public class CallNode extends CFGNode {

    // Source location for late reported errors
    public final Parser.Lexer _loc;

    public CallNode(Parser.Lexer loc, Node... nodes) { super(nodes); _loc = loc; }

    @Override
    public String label() { return "Call"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        String fname = null;
        Node fptr = fptr();
        if( fptr._type instanceof TypeFunPtr tfp && tfp.isConstant() )
            fname = tfp._name;
        if( fname==null ) fptr._print0(sb,visited);
        else sb.append(fname);
        sb.append("( ");
        for( int i=2; i<nIns()-1; i++ )
            in(i)._print0(sb,visited).append(",");
        sb.setLength(sb.length()-1);
        return sb.append(")");
    }

    Node ctrl() { return in(0); }
    Node mem () { return in(1); }
    // Args mapped 1-to-1 on inputs
    Node arg(int idx) { return in(idx); }
    // args from input 2 to last
    public Node fptr() { return _inputs.last(); }

    // Find the Call End from the Call
    public CallEndNode cend() {
        // Always in use slot 0
        if( nOuts()>0 && out(0) instanceof CallEndNode cend ) {
            assert _cend()==cend;
            return cend;
        } else {
            assert _cend()==null;
            return null;
        }
    }
    private CallEndNode _cend() {
        CallEndNode cend=null;
        for( Node n : _outputs )
            if( n instanceof CallEndNode cend0 )
                { assert cend == null; cend = cend0; }
        return cend;
    }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.
    @Override public CFGNode uctrl() { return cend(); }


    @Override
    public Type compute() {
        return ctrl()._type;
    }

    @Override
    public Node idealize() {
        CallEndNode cend = cend();
        if( cend==null ) return null; // Still building

        // Link: call calls target function.  Linking makes the target FunNode
        // point to this Call, and all his Parms point to the call arguments;
        // also the CallEnd points to the Return.
        if( fptr()._type instanceof TypeFunPtr tfp ) {
            // If fidxs is negative, then infinite unknown functions
            long fidxs = tfp.fidxs();
            if( fidxs > 0 ) {
                // Wipe out the return which matching in the linker table
                // Walk the (63 max) bits and link
                for( ; fidxs!=0; fidxs = TypeFunPtr.nextFIDX(fidxs) ) {
                    int fidx = Long.numberOfTrailingZeros(fidxs);
                    TypeFunPtr tfp0 = tfp.makeFrom(fidx);
                    FunNode fun = CodeGen.CODE.link(tfp0);
                    if( fun!=null && !fun._folding && !linked(fun) )
                        link(fun);
                }
            }
        }

        return null;
    }

    // True if Fun is linked to this Call
    boolean linked( FunNode fun ) {
        for( Node n : fun._inputs )
            if( n == this )
                return true;
        return false;
    }


    // Link so this calls fun
    private void link( FunNode fun ) {
        assert !linked(fun);
        fun.addDef(this);
        for( Node use : fun._outputs )
            if( use instanceof ParmNode parm )
                parm.addDef(parm._idx==0 ? cend() : arg(parm._idx));
        // Call end points to function return
        cend().addDef(fun.ret());
        assert linked(fun);
    }

    @Override
    public Parser.ParseException err() {
        if( !(fptr()._type instanceof TypeFunPtr tfp) )
            throw Utils.TODO();
        if( !tfp.notNull() )
            return Parser.error( "Might be null calling "+tfp, _loc);
        // Check for args
        for( int i=0; i<tfp.nargs(); i++ )
            if( !arg(i+2)._type.isa(tfp.arg(i)) )
                return Parser.error( "Argument #"+i+" isa "+arg(i+2)._type+", but must be a "+tfp.arg(i), _loc);

        return null;
    }

}
