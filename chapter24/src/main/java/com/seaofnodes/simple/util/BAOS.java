package com.seaofnodes.simple.util;

import java.util.HashMap;
import java.util.Arrays;

// "ByteArrayOutputStream", and Input Stream and not synchronized and has
// matching pack/unpack strategies.
public class BAOS {

    private byte[] _buf;
    private int _len;

    public BAOS() { _buf = new byte[1]; }
    public BAOS(byte[] buf) { _buf = buf; }

    public byte[] toByteArray() {
        return Arrays.copyOf(_buf,_len);
    }

    public byte[] buf() { return _buf; }
    public int size() { return _len; }
    public void set( byte[] buf, int len ) { _buf=buf; _len=len; }

    // -----------------------------------------------------------------------
    // Write a packed integer to the BAOS.  Track a histogram to make more
    // intelligent choices in the future.
    private static final HashMap<Long,Integer> PACKED_HISTOGRAM = new HashMap<>();

    private void grow(int len) {
        while( _len+len >= _buf.length )
            _buf = Arrays.copyOf(_buf,_buf.length*2);
    }

    // Write a byte, grow as needed
    public void write(int b) {
        grow(1);
        _buf[_len++] = (byte)b;
    }
    // Read an UNSIGNED byte, AIOOBE if off end
    public int read() { return _buf[_len++] & 0xFF; }

    public void write4( int x ) {
        grow(4);
        _buf[_len++] = (byte)(x>> 0);
        _buf[_len++] = (byte)(x>> 8);
        _buf[_len++] = (byte)(x>>16);
        _buf[_len++] = (byte)(x>>24);
    }
    public void write8( long x ) {
        write4((int) x     );
        write4((int)(x>>32));
    }


    // Write byte array
    public void write( byte[] bs ) {
        grow(bs.length);
        System.arraycopy(bs,0,_buf,_len,bs.length);
        _len += bs.length;
    }

    // Read/fill byte array
    public byte[] read( byte[] bs ) {
        System.arraycopy(_buf,_len,bs,0,bs.length);
        _len += bs.length;
        return bs;
    }

    public void packed4(int x) {
        Integer cnt = PACKED_HISTOGRAM.get(x);
        PACKED_HISTOGRAM.put((long)x,cnt==null ? 1 : cnt+1);

        // If 'x' fits in byte range, 1 byte, else 0x80 and then 4 bytes
        if( -127 <= x && x <= 127 ) { write(x); return; }
        write(0x80);
        for( int i = 0; i < 4; i++ ) {
            write(x);
            x >>= 8;
        }
    }
    public int packed4() {
        int x = read();
        if( x!=0x80 ) return x;
        x=0;
        for( int i = 0; i < 4; i++ )
            x |= read()<<(i<<3);
        return x;
    }

    public void packed8(long x) {
        Integer cnt = PACKED_HISTOGRAM.get(x);
        PACKED_HISTOGRAM.put(x,cnt==null ? 1 : cnt+1);

        // If 'x' fits in byte range, 1 byte, else 0x80 and then 4 bytes
        if( -127 <= x && x <= 127 ) { write((int)x); return; }
        write(0x80);
        for( int i = 0; i < 8; i++ ) {
            write((int)x);
            x >>= 8;
        }
    }

    public long packed8() {
        long x = read();
        if( x!=0x80 ) return x;
        x = 0;
        for( int i = 0; i < 8; i++ )
            x |= (long) read() <<(i<<3);
        return x;
    }

    public void packedS(String s) {
        packed4(s.length());
        for( int i=0; i<s.length(); i++ )
            packed4(s.charAt(i));
    }

    public String packedS() {
        int len = packed4();
        String s = new String(_buf,_len,len);
        _len += len;
        return s;
    }
}
