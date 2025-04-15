package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.type.*;
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

    public Node ctrl() { return in(0); }
    public Node mem () { return in(1); }
    public Node expr() { return in(2); }
    public Node rpc () { return in(3); }
    @Override public FunNode fun() { return _fun; }

    @Override
    public String label() { return "Return"; }

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
        if( inProgress () ) return TypeTuple.RET; // In progress
        if( _fun.isDead() ) return TypeTuple.RET.dual(); // Dead another way
        return TypeTuple.make(ctrl()._type,mem()._type,expr()._type);
    }

    @Override public Node idealize() {
        if( inProgress () ) return null;
        if( _fun.isDead() ) return null;

        // Upgrade signature based on return type
        Type ret = expr()._type;
        TypeFunPtr fcn = _fun.sig();
        assert ret.isa(fcn.ret());
        if( ret != fcn.ret() )
            _fun.setSig(fcn.makeFrom(ret));

        // If dead (cant be reached; infinite loop), kill the exit values
        if( ctrl()._type==Type.XCONTROL &&
            !(mem() instanceof ConstantNode && expr() instanceof ConstantNode) ) {
            Node top = new ConstantNode(Type.TOP).peephole();
            setDef(1,top);
            setDef(2,top);
            return this;
        }

        return null;
    }

    public boolean inProgress() {
        return ctrl().getClass() == RegionNode.class && ((RegionNode)ctrl()).inProgress();
    }

    // Gather parse-time return types for error reporting
    private Type mt = Type.TOP;
    private boolean ti=false, tf=false, tp=false, tn=false;

    // Add a return exit to the current parsing function
    void addReturn( Node ctrl, Node rmem, Node expr ) {
        assert inProgress();

        // Gather parse-time return types for error reporting
        Type t = expr._type;
        mt = mt.meet(t);
        ti |= t instanceof TypeInteger x;
        tf |= t instanceof TypeFloat   x;
        tp |= t instanceof TypeMemPtr  x;
        tn |= t==Type.NIL;

        // Merge path into the One True Return
        RegionNode r = (RegionNode)ctrl();
        // Assert that the Phis are in particular outputs; not reordered or shuffled
        PhiNode mem = (PhiNode)r.out(0); assert mem._declaredType == TypeMem.BOT;
        PhiNode rez = (PhiNode)r.out(1); assert rez._declaredType == Type.BOTTOM;
        // Pop "inProgress" null off
        r  ._inputs.pop();
        mem._inputs.pop();
        rez._inputs.pop();
        // Add new return point
        r  .addDef(ctrl);
        mem.addDef(rmem);
        rez.addDef(expr);
        // Back to being inProgress
        r  .addDef(null);
        mem.addDef(null);
        rez.addDef(null);
    }

    @Override public Parser.ParseException err() {
        return expr()._type/*mt*/==Type.BOTTOM ? mixerr(ti,tf,tp,tn,_fun._loc) : null;
    }

    static Parser.ParseException mixerr( boolean ti, boolean tf, boolean tp, boolean tn, Parser.Lexer loc ) {
        if( !ti && !tf && !tp && !tn )
            return Parser.error("No defined return type", loc);
        SB sb = new SB().p("No common type amongst ");
        if( ti ) sb.p("int and ");
        if( tf ) sb.p("f64 and ");
        if( tp || tn ) sb.p("reference and ");
        return Parser.error(sb.unchar(5).toString(),loc);
    }


    // ------------
    // MachNode specifics, shared across all CPUs
    public String op() {
        return _fun._frameAdjust > 0 ? "addi" : "ret";
    }
    // Correct Nodes outside the normal edges
    public void postSelect(CodeGen code) {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
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
            sb.p("rsp += #").p(frameAdjust).p("\nret");
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() )
            // Prints return reg (either RAX or XMM0), RPC and then the
            // callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i),fun())).p("  ");
        // If we did not get the expected rpc, print which one we got
        else if( code._regAlloc.regnum(rpc()) != code._mach.rpc() )
            sb.p("[").p(code.reg(rpc())).p("]");
    }

}
