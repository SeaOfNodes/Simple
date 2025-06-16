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
    // Basic read, write (expand)

    private void grow(int len) {
        while( _len+len >= _buf.length )
            _buf = Arrays.copyOf(_buf,_buf.length*2);
    }

    // Write a byte, grow as needed; silent chop
    public BAOS write(int b) {
        grow(1);
        _buf[_len++] = (byte)b; // Silent chop
        return this;
    }
    // Read an UNSIGNED byte, AIOOBE if off end
    public int read() { return _buf[_len++] & 0xFF; }

    // write 2 bytes
    public BAOS write2( int x ) {
        grow(2);
        _buf[_len++] = (byte)(x>> 0);
        _buf[_len++] = (byte)(x>> 8);
        return this;
    }

    // Read a u16 in 2 bytes
    public int read2() { return read() | (read()<<8);  }

    // write 4 bytes
    public BAOS write4( int x ) {
        grow(4);
        _buf[_len++] = (byte)(x>> 0);
        _buf[_len++] = (byte)(x>> 8);
        _buf[_len++] = (byte)(x>>16);
        _buf[_len++] = (byte)(x>>24);
        return this;
    }

    // Read a i32 in 4 bytes
    public int read4() { return read2() | (read2()<<16); }

    // write 8 bytes
    public BAOS write8( long x ) {
        write4((int) x     );
        write4((int)(x>>32));
        return this;
    }

    // Read a i64 in 8 bytes
    public long read8() { return ((long)read4() & 0xFFFFFFFFL) | ((long)read4()<<32); }

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

    // -----------------------------------------------------------------------
    // Write a packed integer to the BAOS.  Track a histogram to make more
    // intelligent choices in the future.
    private static final HashMap<Long,Integer> PACKED_HISTOGRAM = new HashMap<>();
    private BAOS histo(long x) {
        Integer cnt = PACKED_HISTOGRAM.get(x);
        PACKED_HISTOGRAM.put(x,cnt==null ? 1 : cnt+1);
        return this;
    }

    // Write a u8 (checked)
    public void packed1(int x) {
        assert 0 <= x && x <= 255;
        histo(x).write(x);
    }

    public int packed1() { return read(); }

    // Write a u16 (checked)
    public void packed2(int x) {
        assert 0 <= x && x <= 65535;
        ((0 <= x && x <= 254 ) ? write(x) : write(0xFF).write2(x)).histo(x);
    }

    // Read a packed u16
    public int packed2() {
        int x = read();
        return x==0xFF ? read2() : x;
    }

    // Write a packed i32
    public void packed4(int x) {
        ((-127 <= x && x <= 127 ) ? write(x) : write(-128).write4(x)).histo(x);
    }

    // Read a packed i32
    public int packed4() {
        int x = _buf[_len++];   // Signed byte read
        return x== -128 ? read4() : x;
    }

    // Write a packed i64
    public void packed8(long x) {
        ((-127 <= x && x <= 127 ) ? write((int)x) : write(-128).write8(x)).histo(x);
    }

    // Read a packed i64
    public long packed8() {
        int x = _buf[_len++];   // Signed byte read
        return x== -128 ? read8() : x;
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
