package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FloatPrecisionTest {
    @Test public void testFloatPrecision() {
        String[] ops = {"+1.0","-1.0","*3.0","/3.0"};
        long[] args = {16777216,-16777216,16777215,1};
        double[] results = {16777217.0,-16777217.0,50331645.0,1.0/3.0};
        for( int i = 0; i<ops.length; i++ ) {
            String expr = "x"+ops[i];
            assertEquals(expr,Double.toString(results[i]),
                         evaluate("f32 x = arg; return "+expr+";",args[i]));
            assertEquals(expr,Double.toString((float)results[i]),
                         evaluate("f32 x = arg; f32 y = "+expr+"; return y;",args[i]));
        }
    }

    private static String evaluate(String src, long arg) {
        CodeGen code = new CodeGen(src).parse().iter();
        return Eval2.eval(code,arg);
    }

    @Test public void testMonotoneCompute() {
        new CodeGen("");
        ArrayList<Type> inputs = new ArrayList<>(Arrays.asList(
            Type.TOP,Type.BOTTOM,TypeFloat.F32,TypeFloat.F64,
            TypeFloat.F32.dual(),TypeFloat.F64.dual()));
        for( double value : new double[]{-0.0,0.0,-1.0,1.0,3.0,16777216.0,16777217.0,
                                          Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY} )
            inputs.add(TypeFloat.constant(value));
        // Isolated analysis inputs; no peepholes can fold the tested nodes.
        Node lhs = new ConstantNode(Type.BOTTOM);
        Node rhs = new ConstantNode(Type.BOTTOM);
        for( Node op : new Node[]{new AddFNode(lhs,rhs),new SubFNode(lhs,rhs),
                                  new MulFNode(lhs,rhs),new DivFNode(lhs,rhs)} ) {
            Type[][] results = new Type[inputs.size()][inputs.size()];
            for( int i = 0; i<inputs.size(); i++ )
                for( int j = 0; j<inputs.size(); j++ ) {
                    lhs._type = inputs.get(i);
                    rhs._type = inputs.get(j);
                    results[i][j] = op.compute();
                }
            for( int i = 0; i<inputs.size(); i++ )
                for( int j = 0; j<inputs.size(); j++ )
                    if( inputs.get(i).isa(inputs.get(j)) )
                        for( int k = 0; k<inputs.size(); k++ )
                            if( !results[i][k].isa(results[j][k]) || !results[k][i].isa(results[k][j]) )
                                fail(op.label()+": "+inputs.get(i)+" -> "+inputs.get(j)+
                                     ", other="+inputs.get(k)+"; lhs results "+results[i][k]+" -> "+results[j][k]+
                                     "; rhs results "+results[k][i]+" -> "+results[k][j]);
        }
    }
}
