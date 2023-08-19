/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is Cliff Click.
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.seaofnodes.util;

import java.util.Iterator;

// Standard bit-set but supports the notion of an 'infinite extension' of 1.
// i.e. all bits past the end are either 0 or 1.
// Supports update-in-place, mutable, NOT hash-consed
public class IBitSet implements Iterable<Integer> {
  private boolean _sign; // false=infinite zeros, true=infinite ones
  private final AryInt _bits = new AryInt();

  // Since mutable, please do not mutate these
  public static final IBitSet EMPTY = new IBitSet();
  public static final IBitSet FULL  = new IBitSet().flip();

  private static int idx (int i) { return i>>5; }
  private static int mask(int i) { return 1<<(i&31); }

  // Test; returns the value
  public  boolean  tst(int idx) { return _sign != _tst(idx); }
  private boolean _tst(int idx) { return (_bits.atX(idx(idx)) & mask(idx)) != 0; }

  // Test and set; returns the old value
  public boolean set(int idx) { return _sign ? !_clr(idx) : _set(idx); }
  public boolean clr(int idx) { return _sign ? !_set(idx) : _clr(idx); }

  private boolean _set(int idx) {
    int widx = idx (idx);
    int mask = mask(idx);
    int bits = _bits.atX(widx);
    _bits.setX(widx, bits | mask);
    return (bits&mask)!=0;
  }
  private boolean _clr(int idx) {
    int widx = idx (idx);
    int mask = mask(idx);
    int bits = _bits.atX(widx);
    _bits.setX(widx, bits & ~mask);
    while( _bits._len>0 && _bits.last()==0 )  _bits.pop(); // Shrink
    return (bits&mask)!=0;
  }
  // Constant-time, invert all bits
  public IBitSet flip() { _sign=!_sign; return this; }

  public int bitCount() {
    assert !_sign;              // Infinite if sign is set
    int sum=0;
    for( int i=0; i<_bits._len; i++ )
      sum += Integer.bitCount(_bits._es[i]);
    return sum;
  }
  public int max( ) {
    assert !_sign;              // Infinite if sign is set
    if( _bits._len==0 ) return 0;
    return (31 - Integer.numberOfLeadingZeros(_bits.last()))+((_bits._len-1)<<5);
  }
  
  private int wd(int x) { return _bits._es[x]; }
  private int xd(int x) {
    int b = _bits.atX(x); // zero extend if off end
    return _sign ? ~b : b;// adjust for sign
  }

  public IBitSet clear() {
    _sign=false;
    _bits.clear();
    return this;
  }

  // OR-into-this
  public IBitSet or( IBitSet bs ) {
    _sign |= bs._sign;
    for( int i=0; i<bs._bits._len; i++ )
      _bits.setX(i,_bits.atX(i)|bs.wd(i));
    return this;
  }
  // SUBTRACT-from-this
  public void subtract( IBitSet bs ) {
    for( int i=0; i<bs._bits._len; i++ )
      _bits.setX(i,_bits.atX(i)&~bs.wd(i));
  }
  // True if set is empty.  The flipped set is never empty.
  public boolean is_empty() { return _bits._len==0 && !_sign; }

  // False if any bits in common
  public boolean disjoint( IBitSet bs ) {
    if(    is_empty() ) return true; // Empty set must be disjoint
    if( bs.is_empty() ) return true; // Empty set must be disjoint
    if( _sign && bs._sign ) return false; // Both extensions are set
    IBitSet min = this;
    if( _bits._len > bs._bits._len ) { min=bs; bs=this; } // max in bs
    if( min._sign && min._bits._len < bs._bits._len ) return false; // Extension in min overlaps last bits in max
    for( int i=0; i<min._bits._len; i++ )
      if( (min.wd(i)&bs.wd(i))!= 0 )
        return false;
    return true;
  }

  // Does 'this' subset 'bs'?
  // True if all bits in common: "bs== this.OR (bs)".
  public boolean subsetsX( IBitSet bs ) {
    assert !bs.is_empty();      // Undefined
    int max=Math.max(_bits._len,bs._bits._len);
    for( int i=0; i<max; i++ )
      if( (xd(i)|bs.xd(i)) != bs.xd(i) )     // All bits in common
        return false;
    return true;
  }

  @Override public String toString() { return toString(new SB()).toString(); }
  public SB toString(SB sb) {
    if( _bits._len==0 ) return sb.p(_sign?"[...]":"[]");
    int x = -1;                 // No range-in-process
    sb.p('[');
    for( int i=0; i<_bits._len*32+1; i++ ) {
      if( tst(i) ) {
        if( x==-1 ) x=i;        // Start a range
      } else {
        if( x!=-1 ) {           // End a range
          if( x+1==i ) sb.p(x).p(',');
          else if( x+2==i ) sb.p(x).p(',').p(i-1).p(',');
          else sb.p(x).p("...").p(i-1).p(',');
          x = -1;
        }
      }
    }
    if( x != -1 ) sb.p(x).p("...,"); // Close open range
    return sb.unchar().p(']');
  }

  /** @return an iterator */
  @Override public Iterator<Integer> iterator() { return new Iter(); }
  private class Iter implements Iterator<Integer> {
    int _i=-1;
    @Override public boolean hasNext() {
      int idx;
      while( (idx=idx(++_i)) < _bits._len )
        if( (_bits._es[idx]&mask(_i)) != 0 )
          return true;
      return false;
    }
    @Override public Integer next() {
      if( idx(_i) < _bits._len ) return _i;
      throw new java.util.NoSuchElementException();
    }
  }
}
