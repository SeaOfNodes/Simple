package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public Type _declaredType;
    public final int _idx;             // Argument index

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label,inputs);
        _idx = idx;
        _declaredType = declaredType;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label, parm._declaredType ); _idx = parm._idx; _declaredType = parm._declaredType; }

    @Override public String label() { return MemOpNode.mlabel(_label); }

    public FunNode fun() { return (FunNode)in(0); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( "main".equals(fun()._name) && _label.equals("arg") )
            return p.p("arg");
        p.p("Parm_").p(_label).open();
        for( Node in : _inputs ) p.n(in).p(",");
        return p.unchar(',').close();
    }


    // Always in-progress until we run out of unknown callers
    @Override public boolean inProgress() { return in(0) instanceof FunNode fun && fun.inProgress(); }

    @Override
    public Type compute() {
        Type t;
        if( _idx==1 && region() instanceof FunNode fun ) {
            // Even an unknown caller supplies Start's discovered escape set.
            // Its heap contents are arbitrary, but its addresses are not.
            t=Type.TOP;
            for( int i=1; i<nIns(); i++ )
                if( !addDep(fun.in(i))._type.isHigh() ) t=t.meet(in(i)._type);
            t=t.join(_declaredType);
        } else t = inProgress() ? _declaredType : super.compute().join(_declaredType);
        // Recursion is a loop in disguise. A Parm merges the first argument
        // with those from recursive calls, just as a loop Phi merges its entry
        // and backedge. Use the widening rule in PhiNode.compute here too.
        // CallEndNode.compute handles the separate cycle through return values;
        // widening this Parm alone cannot stop f(x-1)+1 from growing its result.
        if( t instanceof TypeInteger ti && t != _type &&
            !ti.isHigh() && !ti.isConstant() &&
            (!(_type instanceof TypeInteger oldi) || ti._widen <= oldi._widen) )
            return ti.same_but_slightly_wider_than(_declaredType);
        return t;
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof FunNode) )
            return in(1);       // Input has collapse to e.g. starting control.
        // If function is folding, do all possible peeps
        if( fun()._folding ) return super.idealize();

        // Can upgrade minType even while in-progress
        if( _declaredType instanceof TypeMemPtr tmp && _declaredType.isFRef() ) {
            TypeMemPtr tmp2 = (TypeMemPtr) Parser.TYPES.get(tmp._obj._name);
            if( tmp2!=null && tmp2 != _declaredType ) {
                _declaredType = tmp2;
                return this;
            }
        }
        // Skip most phi optimizations on parms
        return null;
    }

    @Override public boolean eq( Node n ) {
        return ((ParmNode)n)._idx==_idx && super.eq(n);
    }

    @Override boolean _upgradeType(java.util.HashMap<String,Type> types) {
        Type next = _declaredType.upgradeType(types);
        if( next==_declaredType ) return false;
        unlock();
        _declaredType=next;
        return true;
    }
}
