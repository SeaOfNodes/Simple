package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MinusNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinusNodeTest {
    @Test public void testMonotoneCompute() {
        CodeGen code = new CodeGen("return 0;");
        ArrayList<Type> inputs = new ArrayList<>(Arrays.asList(Type.gather()));
        for( long value : new long[]{Long.MIN_VALUE,Long.MIN_VALUE+1,-1,1,Long.MAX_VALUE} )
            inputs.add(TypeInteger.constant(value));
        for( double value : new double[]{-0.0,0.0,-1.0,1.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY} )
            inputs.add(TypeFloat.constant(value));
        Type[][] expected = new Type[3][inputs.size()];
        for( CodeGen.Phase phase : new CodeGen.Phase[]{CodeGen.Phase.Parse,CodeGen.Phase.Iter,CodeGen.Phase.Opto,CodeGen.Phase.IterPostOpto} ) {
            code.driver(phase);
            for( byte mode=0; mode<3; mode++ ) {
                // An isolated analysis input: vary its type without peepholes
                // selecting a mode or folding the node under test.
                Node input = ConstantNode.seed(Type.BOTTOM);
                input.setDef(0,null);
                MinusNode minus = new MinusNode(input,mode);
                Type[] results = new Type[inputs.size()];
                for( int i=0; i<inputs.size(); i++ ) {
                    input._type = inputs.get(i);
                    results[i] = minus.compute();
                    if( phase==CodeGen.Phase.Parse ) expected[mode][i] = results[i];
                    else assertSame("Phase changed compute",expected[mode][i],results[i]);
                }
                // The same order relation covers both falling SCCP types
                // and rising Iter types, including crossing numeric families.
                for( int i=0; i<inputs.size(); i++ )
                    for( int j=0; j<inputs.size(); j++ )
                        if( inputs.get(i).isa(inputs.get(j)) )
                            assertTrue("mode="+mode+": "+inputs.get(i)+" -> "+inputs.get(j)+
                                       " gave "+results[i]+" -> "+results[j],results[i].isa(results[j]));
            }
        }
    }

    @Test public void testUnresolvedInput() {
        new CodeGen("return 0;").driver(CodeGen.Phase.Opto);
        Node input = ConstantNode.seed(TypeInteger.TRUE);
        MinusNode minus = new MinusNode(input);
        assertSame(TypeInteger.constant(-1),minus.compute());
        input._type = Type.BOTTOM;
        assertSame(Type.BOTTOM,minus.compute());
        minus.setMode((byte)1);
        assertSame(Type.BOTTOM,minus.compute());
    }

    @Test public void testRecursiveFloat() {
        CodeGen code = new CodeGen("val _neg = { int x -> x ? -_neg(x-1) : 1.0; }; return _neg(arg);")
            .driver(CodeGen.Phase.TypeCheck);
        for( int i=0; i<4; i++ )
            assertEquals((i&1)==0 ? "1.0" : "-1.0",Eval2.eval(code,i));
    }
}
