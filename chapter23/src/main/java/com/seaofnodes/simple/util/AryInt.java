package com.seaofnodes.simple.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.IntUnaryOperator;

// ArrayList with saner syntax
public class AryInt {
  public int[] _es;
  public int _len;
  public AryInt(int[] es) { this(es,es.length); }
  public AryInt(int[] es, int len) { _es=es; _len=len; }
  public AryInt() { this(new int[1],0); }

  /** @return list is empty */
  public boolean isEmpty() { return _len==0; }
  /** @return active list length */
  public int len() { return _len; }
  /** @param i element index
   *  @return element being returned; throws if OOB */
  public int at( int i ) {
    range_check(i);
    return _es[i];
  }
  /** @param i element index
   *  @return element being returned, or 0 if OOB */
  public int atX( int i ) {
    return i < _len ? _es[i] : 0;
  }
  /** @return last element */
  public int last( ) { return at(_len-1); }
  public int last(int x ) { return at(_len-1+x); }

  /** @return remove and return last element */
  public int pop( ) {
    range_check(0);
    return _es[--_len];
  }

  /** Add element in amortized constant time
   *  @param e element to add at end of list
   *  @return 'this' for flow-coding */
  public AryInt push( int e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  /** Slow, linear-time, element insert.  Preserves order.
   *  @param i index to insert at, between 0 and _len inclusive.
   *  @param e intlement to insert
   */
  public void insert( int i, int e ) {
    if( i < 0 || i>_len )
      throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    System.arraycopy(_es,i,_es,i+1,(_len++)-i);
    _es[i] = e;
  }

  /** Fast, constant-time, element removal.  Does not preserve order
   *  @param i element to be removed
   *  @return element removed */
  public int del( int i ) {
    range_check(i);
    int tmp = _es[i];
    _es[i]=_es[--_len];
    return tmp;
  }

  /** Slow, linear-time, element removal.  Preserves order.
   *  @param i element to be removed
   *  @return element removed */
  public int remove( int i ) {
    range_check(i);
    int e = _es[i];
    System.arraycopy(_es,i+1,_es,i,(--_len)-i);
    return e;
  }

  /** Remove all elements */
  public void clear( ) { Arrays.fill(_es,0,_len,0); _len=0; }

  // Extend and set
  public int setX( int i, int e ) {
    while( i>= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    if( i >= _len ) _len = i+1;
    return (_es[i] = e);
  }

  public int set( int i, int e ) {
    range_check(i);
    return (_es[i] = e);
  }

  public AryInt set_as( int e ) { _es[0] = e; _len=1; return this; }
  public AryInt set_len( int len ) {
    if( len > _len )
      while( len>= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    _len = len;
    while( _es.length > (len<<1) ) // Shrink if hugely too large
      _es = Arrays.copyOf(_es,_es.length>>1);
    return this;
  }

  /** @param c Collection to be added */
  public AryInt addAll( Collection<? extends Integer> c ) { for( int e : c ) push(e); return this; }

  /** @param es Array to be added */
  public AryInt addAll( int[] es ) {
    if( es.length==0 ) return this;
    while( _len+es.length > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    System.arraycopy(es,0,_es,_len,es.length);
    _len += es.length;
    return this;
  }

  /** @param es Array to be added */
  public AryInt addAll( AryInt ary ) {
    if( ary._len==0 ) return this;
    while( _len+ary._len > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    System.arraycopy(ary._es,0,_es,_len,ary._len);
    _len += ary._len;
    return this;
  }

  public AryInt map_update( IntUnaryOperator f ) { for( int i = 0; i<_len; i++ ) _es[i] = f.applyAsInt(_es[i]); return this; }

  /** @return compact array version, using the internal base array where possible. */
  public int[] asAry() { return _len==_es.length ? _es : Arrays.copyOf(_es,_len); }

  /** Sorts in-place */
  public void sort_update() { Arrays.sort(_es, 0, _len);  }
  /** Find the first matching element using ==, or -1 if none.  Note that
   *  most del calls shuffle the list, so the first element might be random.
   *  @param e intlement to find
   *  @return index of first matching element, or -1 if none */
  public int find( int e ) {
    for( int i=0; i<_len; i++ )  if( _es[i]==e )  return i;
    return -1;
  }

  @Override public String toString() {
    SB sb = new SB().p('[');
    for( int i=0; i<_len; i++ )
      sb.p(_es[i]).p(',');
    return sb.unchar().p(']').toString();
  }

  private void range_check( int i ) {
    if( i < 0 || i>=_len )
      throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
  }

  // Binary search sorted _es.  Returns insertion point.
  // Undefined results if _es is not sorted.
  public int binary_search( int e ) {
    int lo=0, hi=_len-1;
    while( lo <= hi ) {
      int mid = (hi + lo) >>> 1; // midpoint, rounded down
      int mval = _es[mid];
      if( e==mval ) {
        // If dups, get to the first.
        while( mid>0 && e==_es[mid-1] ) mid--;
        return mid;
      }
      if( e >mval ) lo = mid+1;
      else          hi = mid-1;
    }
    return lo;
  }

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof AryInt ary) ) return false;
    if( _len != ary._len ) return false;
    if( _es == ary._es ) return true;
    for( int i=0; i<_len; i++ )
      if( _es[i] != ary._es[i] )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    int sum=_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i];
    return sum;
  }
}
