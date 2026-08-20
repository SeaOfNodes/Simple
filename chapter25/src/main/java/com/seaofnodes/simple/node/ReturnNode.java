package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;

/**
 * The Return node has two inputs.  The first input is a control node and the
 * second is the data node that supplies the return value.
 * <p>
 * In this presentation, Return functions as a Stop node, since multiple <code>return</code> statements are not possible.
 * The Stop node will be introduced in Chapter 6 when we implement <code>if</code> statements.
 * <p>
 * The Return's output is the value from the data node.
 */
public class ReturnNode extends CFGNode {

    public FunNode _fun;

    public ReturnNode(Node ctrl, Node mem, Node data, Node rpc, FunNode fun ) {
        super(ctrl, mem, data, rpc);
        _fun = fun;
    }
    public ReturnNode( ReturnNode ret, FunNode fun ) { super(ret);  _fun = fun;  }
    @Override public Tag serialTag() { return Tag.Return; }

    public Node ctrl() { return in(0); }
    public Node mem () { return in(1); }
    public Node expr() { return in(2); }
    public Node rpc () { return in(3); }
    @Override public FunNode fun() { return _fun; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("return ");
        if( expr()==null ) sb.append("----");
        else expr()._print0(sb, visited);
        return sb.append(";");
    }

    // No one unique control follows; can be many call end sites
    @Override public CFGNode uctrl() { return null; }

    @Override
    public Type compute() {
        return TypeTuple.make(ctrl()._type,mem()._type,expr()._type);
    }

    @Override public Node idealize() {
        if( _fun.isDead() ) return null;

        // Upgrade signature based on return type
        Type ret = expr()._type;
        TypeFunPtr fcn = _fun.sig();
        if( ret != fcn.ret() && ret.isa(fcn.ret()) )
            _fun.setSig(fcn.makeFrom(ret));

        return null;
    }

    @Override public Parser.ParseException err() {
        if( ctrl()._type == Type.CONTROL &&
            expr()._type == Type.TOP )
            return Parser.error("No defined return type",null);
        if( ctrl()._type == Type.CONTROL &&
            expr()._type == TypeScalar.BOT )
            return Parser.error("Cannot return generic scalar",null);
        // With no user constructor, <init> doubles as the public no-arg
        // constructor.  Required fields remain TOP in its private memory: a
        // typed parser poison which deliberately emitted no Store or code.
        if( ctrl()._type == Type.CONTROL && _fun.isInstance() ) {
            TypeStruct self = ((TypeMemPtr)_fun.sig().arg(0))._obj;
            if( !CodeGen.hasUserConstructor(self) && expr()._type instanceof TypeMem mem )
                for( Field fld : self._fields ) {
                    if( !(fld._t instanceof TypeNil tn && tn.notNull()) ) continue;
                    Type actual = mem._alias==1 && mem._t instanceof TypeStruct ts
                        ? ts.field(fld._fname)._t
                        : mem._alias==fld._alias ? mem._t : Type.TOP;
                    if( actual==Type.TOP || actual instanceof TypeNil atn && atn.nullable() )
                        return Parser.error("'"+self._name+"' is not fully initialized, field '"+
                                            fld._fname+"' is only partially set in the constructor",null);
                }
        }
        return null;
    }

    @Override public boolean eq( Node n ) {
        return !_fun.isDead() && !((ReturnNode)n)._fun.isDead();
    }

    // ------------
    // MachNode specifics, shared across all CPUs
    public String op() {
        return _fun._frameAdjust > 0 ? "epilog" : "ret  ";
    }
    // Correct Nodes outside the normal edges
    public void postSelect(CodeGen code) {
        _fun.setRet(this);
    }
    public RegMask regmap(int i) {
        return i==2
            ? CodeGen.CODE._mach.retMask(_fun.sig())
            : CodeGen.CODE._retMasks[i];
    }
    public RegMask outregmap() { return null; }
    public void encoding( Encoding enc ) { throw Utils.TODO(); }
    public void asm(CodeGen code, SB sb) {
        int frameAdjust = fun()._frameAdjust;
        if( frameAdjust>0 )
            sb.p("\n").p("rsp += #").p(frameAdjust).p("\nret    ");
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() < CodeGen.Phase.RegAlloc.ordinal() ||
            (code._phase.ordinal() == CodeGen.Phase.RegAlloc.ordinal() && !code._regAlloc.done()) )
            // Prints return reg (either RAX or XMM0), RPC and then the
            // callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i))).p("  ");
        // If we did not get the expected rpc, print which one we got
        else if( code._regAlloc.regnum(rpc()) != code._mach.rpc() )
            sb.p("[").p(code.reg(rpc())).p("]");
    }

}
