package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntegerIdentityTest {
    private static final String[] OPS = {"And","Or","Xor","Shl","Sar","Shr","Add","Mul","Div"};

    @Test public void testIdentityWaitsForIntegerInput() {
        new Parser("return 0;").parse();
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
        new Parser("return 0;").parse();
        for( Type type : new Type[]{TypeFloat.constant(3.5)} )
            for( String name : OPS ) {
                Node lhs=input(type);
                Node op=op(name,lhs,input(TypeInteger.constant(identity(name))));
                assertNull(name,op.idealize());
            }
    }

    @Test public void testIntegerIdentitiesAndMaskedShiftsStillFold() {
        new Parser("return 0;").parse();
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
            case "And" -> new AndNode(lhs,rhs);
            case "Or" -> new OrNode(lhs,rhs);
            case "Xor" -> new XorNode(lhs,rhs);
            case "Shl" -> new ShlNode(lhs,rhs);
            case "Sar" -> new SarNode(lhs,rhs);
            case "Shr" -> new ShrNode(lhs,rhs);
            case "Add" -> new AddNode(lhs,rhs);
            case "Mul" -> new MulNode(lhs,rhs);
            case "Div" -> new DivNode(lhs,rhs);
            default -> throw new AssertionError(name);
        };
    }
}
