package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.PackedTypeTestSupport;
import com.seaofnodes.simple.util.BAOS;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class BAOSTest {
    @Test public void packedIntBoundaries() {
        int[] values={Integer.MIN_VALUE,-134217729,-134217728,-1048577,-1048576,
            -8193,-8192,-65,-64,-1,0,1,63,64,8191,8192,1048575,1048576,
            134217727,134217728,Integer.MAX_VALUE};
        int[] widths={5,5,4,4,3,3,2,2,1,1,1,1,1,2,2,3,3,4,4,5,5};
        BAOS out=new BAOS();
        for( int i=0; i<values.length; i++ ) {
            int before=out.size();
            out.packed4(values[i]);
            assertEquals("width of "+values[i],widths[i],out.size()-before);
        }
        BAOS in=new BAOS(out.toByteArray());
        for( int value : values ) assertEquals(value,in.packed4());
        assertEquals(out.size(),in.size());
        out.clear();
        out.packed4(-65); out.packed4(64);
        assertArrayEquals(new byte[]{(byte)0x81,1,(byte)0x80,1},out.toByteArray());
    }

    @Test public void packedIntsAndStrings() {
        Random random=new Random(123);
        int[] values=new int[10000];
        BAOS out=new BAOS();
        for( int i=0; i<values.length; i++ ) out.packed4(values[i]=random.nextInt());
        String[] strings={"","ASCII stays compact", "\u0000", "caf\u00e9 \u65e5\u672c\u8a9e \uD83D\uDE00", "x".repeat(8192)};
        for( String s : strings ) out.packedS(s);
        out.packed8(Long.MIN_VALUE);
        BAOS in=new BAOS(out.toByteArray());
        for( int value : values ) assertEquals(value,in.packed4());
        for( String s : strings ) assertEquals(s,in.packedS());
        assertEquals(Long.MIN_VALUE,in.packed8());
        assertEquals(out.size(),in.size());
    }

    @Test public void rejectPackedIntOverflow() {
        for( byte last : new byte[]{0x10,(byte)0x80} ) {
            BAOS in=new BAOS(new byte[]{(byte)0x80,(byte)0x80,(byte)0x80,(byte)0x80,last});
            try { in.packed4(); fail("overflow accepted"); }
            catch( IllegalArgumentException expected ) { assertTrue(expected.getMessage().contains("overflow")); }
        }
    }

    @Test public void wideSingletonFunctionIds() {
        new CodeGen("");
        for( int id : new int[]{2,63,64,254,255,256,1024,65535} )
            PackedTypeTestSupport.singleton(id);
    }
}
