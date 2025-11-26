package com.seaofnodes.simple.codegen;

// Mutable regmask(writable)
public class RegMaskRW extends RegMask {
    public RegMaskRW(long x, long y) { super(x,y);  }
    // clears bit at position r. Returns true if the mask is still not empty.
    public boolean clr(int r) {
        if( r < 64 ) _bits0 &= ~(1L<<(r   ));
        else         _bits1 &= ~(1L<<(r-64));
        return _bits0!=0 || _bits1!=0;
    }
    // sets bit at position r.
    public void set(int r) {
        if( r < 64 ) _bits0 |= (1L<<(r   ));
        else         _bits1 |= (1L<<(r-64));
    }
    @Override RegMaskRW and( RegMask mask ) {
        if( mask==null ) return this;
        _bits0 &= mask._bits0;
        _bits1 &= mask._bits1;
        return this;
    }
    @Override RegMaskRW sub( RegMask mask ) {
        if( mask==null ) return this;
        _bits0 &= ~mask._bits0;
        _bits1 &= ~mask._bits1;
        return this;
    }
    public RegMaskRW or( RegMask mask ) {
        if( mask!=null ) {
            _bits0 |= mask._bits0;
            _bits1 |= mask._bits1;
        }
        return this;
    }
}
