package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.ConvertNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConvertNodeTest {
    @Test public void testMonotoneCompute() {
        CodeGen code = new CodeGen("return 0;");
        ArrayList<Type> inputs = new ArrayList<>(Arrays.asList(
            Type.BOTTOM,Type.CONTROL,Type.NIL,TypeScalar.BOT,TypeMem.BOT));
        TypeInteger.gather(inputs);
        TypeFloat.gather(inputs);
        TypePtr.gather(inputs);
        TypeMemPtr.gather(inputs);
        TypeFunPtr.gather(inputs);
        // Writable arrays require matching element access in both directions.
        // Widening the element's access can make that check pass while keeping
        // a known length; compute must not suddenly gain that length fact.
        for( TypeInteger len : new TypeInteger[]{TypeInteger.U32,TypeInteger.TRUE} )
            for( boolean elementRO : new boolean[]{false,true} )
                for( boolean arrayRO : new boolean[]{false,true} )
                    inputs.add(TypeMemPtr.make(TypeStruct.makeAry("[]test",len,-1,
                        TypeMemPtr.TEST.withAccess(elementRO),-2,false)).withAccess(arrayRO));
        inputs.add(TypeConAryB.make(new byte[]{1,2,3}));
        for( long value : new long[]{Long.MIN_VALUE,-129,-128,-1,0,1,127,128,255,256,Long.MAX_VALUE} )
            inputs.add(TypeInteger.constant(value));
        for( double value : new double[]{-0.0,0.0,1.0,16777217.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY} )
            inputs.add(TypeFloat.constant(value));
        int size = inputs.size();
        for( int i = 0; i<size; i++ ) inputs.add(inputs.get(i).dual());

        ArrayList<Type> destinations = new ArrayList<>();
        for( Type t : inputs )
            if( !t.isHigh() && (t instanceof TypeInteger || t instanceof TypeFloat || t instanceof TypeNil) )
                destinations.add(t);

        // Vary one isolated input without folding or lowering the conversion.
        Node input = ConstantNode.seed(Type.BOTTOM);
        input.setDef(0,null);
        Type[][] expected = new Type[destinations.size()][inputs.size()];
        for( CodeGen.Phase phase : new CodeGen.Phase[]{CodeGen.Phase.Parse,CodeGen.Phase.Iter,CodeGen.Phase.Opto,CodeGen.Phase.IterPostOpto} ) {
            code.driver(phase);
            for( int d = 0; d<destinations.size(); d++ ) {
                Type dst = destinations.get(d);
                ConvertNode convert = new ConvertNode(dst,input);
                Type[] results = new Type[inputs.size()];
                for( int i = 0; i<inputs.size(); i++ ) {
                    input._type = inputs.get(i);
                    results[i] = convert.compute();
                    if( phase==CodeGen.Phase.Parse ) expected[d][i] = results[i];
                    else assertSame("Phase changed compute for "+inputs.get(i)+" to "+dst,expected[d][i],results[i]);
                }
                // Check both rising Iter and falling SCCP with the same order.
                for( int i = 0; i<inputs.size(); i++ )
                    for( int j = 0; j<inputs.size(); j++ )
                        if( inputs.get(i).isa(inputs.get(j)) && !results[i].isa(results[j]) )
                            fail("Convert to "+dst+": "+inputs.get(i)+" -> "+inputs.get(j)+
                                 " gave "+results[i]+" -> "+results[j]);
            }
        }
    }
}
