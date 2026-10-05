package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntegerIdentityTest {
    private static final String[] OPS = {"And","Or","Xor","Shl","Sar","Shr","Add","Mul","Div"};

    @Test public void testIdentityWaitsForIntegerInput() {
        new CodeGen("return 0;").parse();
        for( String name : OPS ) {
            Node lhs = input(Type.BOTTOM);
            Node rhs = input(TypeInteger.constant(identity(name)));
            Node op = op(name,lhs,rhs);
            assertFalse(name,lhs._type.isa(op.compute()));
            assertNull(name,op.idealize());
            // Model a provisional input lifting to int. The same fold can now fire.
            lhs._type = TypeInteger.BOT;
            assertSame(name,lhs,op.idealize());
            assertTrue(name,lhs._type.isa(op.compute()));
        }
    }

    @Test public void testNonIntegerIdentityCannotEraseTheOperator() {
        new CodeGen("return 0;").parse();
        for( Type type : new Type[]{TypeFloat.constant(3.5)} )
            for( String name : OPS ) {
                Node lhs=input(type);
                Node op=op(name,lhs,input(TypeInteger.constant(identity(name))));
                assertNull(name,op.idealize());
            }
    }

    @Test public void testIntegerIdentitiesAndMaskedShiftsStillFold() {
        new CodeGen("return 0;").parse();
        for( String name : OPS )
            for( Type type : new Type[]{TypeInteger.BOT,TypeInteger.make(2,7),TypeInteger.constant(5)} ) {
                Node lhs=input(type);
                long[] identities = name.equals("And") ? new long[]{-1} :
                    name.equals("Shl") || name.equals("Sar") || name.equals("Shr") ? new long[]{0,64,-64} : new long[]{identity(name)};
                for( long identity : identities ) {
                    Node op=op(name,lhs,input(TypeInteger.constant(identity)));
                    assertSame(name,lhs,op.idealize());
                    assertTrue(name,lhs._type.isa(op.compute()));
                }
            }
    }

    @Test public void testDoubleNegationWaitsForIntegerInput() {
        new CodeGen("return 0;").parse();
        for( Type type : new Type[]{Type.BOTTOM,TypeFloat.constant(3.5)} ) {
            Node lhs=input(type);
            Node inner=new MinusNode(lhs);
            inner._type=inner.compute();
            Node outer=new MinusNode(inner);
            assertFalse(lhs._type.isa(outer.compute()));
            assertNull(outer.idealize());
            lhs._type=TypeInteger.make(2,7);
            inner._type=inner.compute();
            assertSame(lhs,outer.idealize());
            assertTrue(lhs._type.isa(outer.compute()));
        }
    }

    // These tests exercise idealize directly, before GVN or constant folding.
    private static Node input(Type type) {
        Node n = new ConstantNode(type);
        n._type=type;
        return n;
    }

    private static long identity(String name) {
        return name.equals("And") ? -1 : name.equals("Mul") || name.equals("Div") ? 1 : 0;
    }

    private static Node op(String name, Node lhs, Node rhs) {
        return switch(name) {
            case "And" -> new AndNode(null,lhs,rhs);
            case "Or" -> new OrNode(null,lhs,rhs);
            case "Xor" -> new XorNode(null,lhs,rhs);
            case "Shl" -> new ShlNode(null,lhs,rhs);
            case "Sar" -> new SarNode(null,lhs,rhs);
            case "Shr" -> new ShrNode(null,lhs,rhs);
            case "Add" -> new AddNode(lhs,rhs);
            case "Mul" -> new MulNode(lhs,rhs);
            case "Div" -> new DivNode(lhs,rhs);
            default -> throw new AssertionError(name);
        };
    }
}
