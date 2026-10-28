package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArithNodeTest {
    @Test public void testUnresolvedInput() {
        new CodeGen("return 0;").driver(CodeGen.Phase.Opto);
        Node lhs = ConstantNode.seed(TypeInteger.TRUE);
        Node rhs = ConstantNode.seed(TypeInteger.TRUE);
        ArithNode add = new AddNode(lhs,rhs);
        assertSame(TypeInteger.constant(2),add.compute());
        lhs._type = TypeScalar.BOT;
        assertSame(Type.BOTTOM,add.compute());
        add.setMode((byte)1);
        assertSame(Type.BOTTOM,add.compute());
    }

    @Test public void testFloatPrecision() {
        CodeGen code = new CodeGen("f32 x = arg; return x+1.0;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("1.6777217E7",Eval2.eval(code,16777216));
        code = new CodeGen("f32 x = arg; f32 y = x+1.0; return y;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("1.6777216E7",Eval2.eval(code,16777216));
    }

    @Test public void testMonotoneCompute() {
        CodeGen code = new CodeGen("return 0;");
        ArrayList<Type> inputs = new ArrayList<>(Arrays.asList(
            Type.BOTTOM,Type.CONTROL,Type.NIL,TypeScalar.BOT,TypeMem.BOT));
        TypeInteger.gather(inputs);
        TypeFloat.gather(inputs);
        TypePtr.gather(inputs);
        TypeMemPtr.gather(inputs);
        TypeFunPtr.gather(inputs);
        inputs.add(TypeConAryB.make(new byte[]{1,2,3}));
        for( long value : new long[]{Long.MIN_VALUE,Long.MIN_VALUE+1,-1,1,Long.MAX_VALUE} )
            inputs.add(TypeInteger.constant(value));
        for( double value : new double[]{-0.0,0.0,-1.0,1.0,16777216.0,16777217.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY} )
            inputs.add(TypeFloat.constant(value));
        int size = inputs.size();
        for( int i = 0; i<size; i++ ) inputs.add(inputs.get(i).dual());
        size = inputs.size();

        // Isolated inputs let us vary types without peepholes settling modes.
        Node lhs = ConstantNode.seed(Type.BOTTOM);
        Node rhs = ConstantNode.seed(Type.BOTTOM);
        lhs.setDef(0,null);
        rhs.setDef(0,null);
        ArrayList<ArithNode> ops = new ArrayList<>();
        for( byte mode = 0; mode<3; mode++ ) {
            ops.add(new AddNode(lhs,rhs,mode));
            ops.add(new SubNode(lhs,rhs,mode));
            ops.add(new MulNode(lhs,rhs,mode));
            ops.add(new DivNode(lhs,rhs,mode));
        }
        ops.add(new AndNode(null,lhs,rhs));
        ops.add(new OrNode(null,lhs,rhs));
        ops.add(new XorNode(null,lhs,rhs));
        ops.add(new ShlNode(null,lhs,rhs));
        ops.add(new SarNode(null,lhs,rhs));
        ops.add(new ShrNode(null,lhs,rhs));
        Type[][][] expected = new Type[ops.size()][size][size];
        for( CodeGen.Phase phase : new CodeGen.Phase[]{CodeGen.Phase.Parse,CodeGen.Phase.Iter,CodeGen.Phase.Opto,CodeGen.Phase.IterPostOpto} ) {
            code.driver(phase);
            for( int op = 0; op<ops.size(); op++ ) {
                ArithNode arith = ops.get(op);
                Type[][] results = new Type[size][size];
                for( int i = 0; i<size; i++ )
                    for( int j = 0; j<size; j++ ) {
                        lhs._type = inputs.get(i);
                        rhs._type = inputs.get(j);
                        results[i][j] = arith.compute();
                        if( phase==CodeGen.Phase.Parse ) expected[op][i][j] = results[i][j];
                        else assertSame("Phase changed compute",expected[op][i][j],results[i][j]);
                    }
                // Check the order relation in each argument independently.
                // This covers both falling SCCP types and rising Iter types.
                for( int i = 0; i<size; i++ )
                    for( int j = 0; j<size; j++ )
                        if( inputs.get(i).isa(inputs.get(j)) )
                            for( int k = 0; k<size; k++ ) {
                                if( !results[i][k].isa(results[j][k]) || !results[k][i].isa(results[k][j]) )
                                    fail(arith.getClass().getSimpleName()+" mode="+arith.mode()+": "+
                                         inputs.get(i)+" -> "+inputs.get(j)+", other="+inputs.get(k)+
                                         "; lhs results "+results[i][k]+" -> "+results[j][k]+
                                         "; rhs results "+results[k][i]+" -> "+results[k][j]);
                            }
            }
        }
    }
}
