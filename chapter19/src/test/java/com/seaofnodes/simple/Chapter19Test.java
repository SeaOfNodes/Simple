package com.seaofnodes.simple;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

public class Chapter19Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }


    @Test
    public void testString() {
        CodeGen code = new CodeGen(
"""
struct String {
    u8[] cs;
    int _hashCode;
};

val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};

val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

val _hashCodeString = { String self ->
    int hash=0;
    if( self.cs ) {
        for( int i=0; i< self.cs#; i++ )
            hash = hash*31 + self.cs[i];
    }
    if( !hash ) hash = 123456789;
    return hash;
};

String !s = new String { cs = new u8[17]; };
s.cs[0] =  67; // C
s.cs[1] = 108; // l
hashCode(s);
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,123456789,Phi(Loop,0,(.[]+(Phi_hash*31)))); return Phi(Region,1,0,0,1); ]", code._stop.toString());
        assertEquals("-2449306563677080489", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

}
