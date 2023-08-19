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

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

// ArrayList with saner syntax
public class Ary<E> implements Iterable<E> {
  public E[] _es;
  public int _len;
  public Ary(E[] es) { this(es,es.length); }
  public Ary(E[] es, int len) { if( es.length==0 ) es=Arrays.copyOf(es,1); _es=es; _len=len; }
  @SuppressWarnings("unchecked")
  public Ary(Class<E> clazz) { this((E[]) Array.newInstance(clazz, 1),0); }

  /** @return list is empty */
  public boolean isEmpty() { return _len==0; }
  /** @return active list length */
  public int len() { return _len; }
  /** @param i element index
   *  @return element being returned; throws if OOB 
   *  @exception ArrayIndexOutOfBoundsException if !(0 <= i < _len)
   */
  public E at( int i ) {
    range_check(i);
    return _es[i];
  }
  /** @param i element index
   *  @return element being returned, or null if OOB */
  public E atX( int i ) {
    return i < _len ? _es[i] : null;
  }
  /** @return last element */
  public E last( ) {
    range_check(0);
    return _es[_len-1];
  }
  public void last(int i) {
    range_check(i);
    E tmp = _es[i];
    _es[i] = _es[_len-1];
    _es[_len-1] = tmp;
  }

  /** @return remove and return last element */
  public E pop( ) {
    range_check(0);
    return _es[--_len];
  }

  /** Add element in amortized constant time
   *  @param e Element to add at end of list
   *  @return 'this' for flow-coding */
  public Ary<E> add( E e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  /** Add element in amortized constant time
   *  @param e Element to add at end of list
   **/
  public E push( E e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    return (_es[_len++] = e);
  }

  /** Slow, linear-time, element insert.  Preserves order.
   *  @param i index to insert at, between 0 and _len inclusive.
   *  @param e Element to insert
   */
  public void insert( int i, E e ) {
    if( i < 0 || i>_len )
      throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    System.arraycopy(_es,i,_es,i+1,(_len++)-i);
    _es[i] = e;
  }

  /** Fast, constant-time, element removal.  Does not preserve order
   *  @param i element to be removed
   *  @return element removed */
  public E del( int i ) {
    range_check(i);
    E tmp = _es[i];
    _es[i]=_es[--_len];
    return tmp;
  }

  /** Element removal, using '=='.  Does not preserve order.
   *  @param e element to be removed
   *  @return element removed */
  public E del( E e ) {
    for( int i=0; i<_len; i++ ) {
      E tmp = _es[i];
      if( tmp==e ) {
        _es[i]=_es[--_len];
        return tmp;
      }
    }
    return null;
  }

  /** Slow, linear-time, element removal.  Preserves order.
   *  @param i element to be removed
   *  @return element removed */
  public E remove( int i ) {
    range_check(i);
    E e = _es[i];
    System.arraycopy(_es,i+1,_es,i,(--_len)-i);
    return e;
  }

  /** Remove all elements */
  public void clear( ) { _len=0; }

  public void fill( E e ) { Arrays.fill(_es,0,_len,e); }

  /** Extend and set.  null fills as needed and does not throw AIOOBE.
   *  @param i element to set
   *  @param e value to set
   *  @return old value
   */
  public E setX( int i, E e ) {
    if( i >= _len ) Arrays.fill(_es,_len,_es.length,null);
    while( i>= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    if( i >= _len ) _len = i+1;
    return (_es[i] = e);
  }

  /** Clear element.  Does nothing if element is OOB, since these are clear by
   *  default.
   *  @param i element to clear
   */
  public void clear( int i ) {  if( i<_len ) _es[i]=null; }

  /** Set existing element
   *  @param i element to set
   *  @param e value to set
   *  @return old value
   *  @exception AIOOBE if !(0 <= i < _len)
   */
  public E set( int i, E e ) {
    range_check(i);
    E old = _es[i];
    _es[i] = e;
    return old;
  }

  // Increment length, revealing the E behind the _len, or null
  public E inc_len( ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    return _es[_len++];
  }
  public Ary<E> set_len( int len ) {
    if( len > _len )
      while( len>= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    _len = len;
    while( _es.length > (len<<1) ) // Shrink if hugely too large
      _es = Arrays.copyOf(_es,_es.length>>1);
    Arrays.fill(_es,len,_es.length,null);
    return this;
  }

  /** @param c Collection to be added */
  public Ary<E> addAll( Collection<? extends E> c ) { if( c!=null ) for( E e : c ) add(e); return this; }

  /** @param es Array to be added */
  public <F extends E> Ary<E> addAll( F[] es ) {
    if( es==null || es.length==0 ) return this;
    while( _len+es.length > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    System.arraycopy(es,0,_es,_len,es.length);
    _len += es.length;
    return this;
  }

  /** @param c Collection to be added */
  public Ary<E> addAll( Ary<? extends E> c ) {
    if( c==null || c._len==0 ) return this;
    while( _len+c._len > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
    System.arraycopy(c._es,0,_es,_len,c._len);
    _len += c._len;
    return this;
  }

  /** @return compact array version */
  public E[] asAry() { return Arrays.copyOf(_es,_len); }

  /** @param f function to apply to each element.  Updates in-place. */
  public Ary<E> map_update( Function<E,E> f ) { for( int i = 0; i<_len; i++ ) _es[i] = f.apply(_es[i]); return this; }
  /** @param P filter out elements failing to pass the predicate; updates in
   *  place and shuffles list.
   *  @return this, for flow-coding */
  public Ary<E> filter_update( Predicate<E> P ) {
    for( int i=0; i<_len; i++ )
      if( !P.test(_es[i]) )
        del(i--);
    return this;
  }
  /** Sorts in-place
   *  @param c Comparator to sort by */
  public void sort_update(Comparator<? super E> c ) { Arrays.sort(_es, 0, _len, c);  }
  /** Find the first matching element using ==, or -1 if none.  Note that
   *  most del calls shuffle the list, so the first element might be random.
   *  @param e Element to find
   *  @return index of first matching element, or -1 if none */
  public int find( E e ) {
    for( int i=0; i<_len; i++ )  if( _es[i]==e )  return i;
    return -1;
  }
  /** Find the first element matching predicate P, or -1 if none.  Note that
   *  most del calls shuffle the list, so the first element might be random.
   *  @param P Predicate to match
   *  @return index of first matching element, or -1 if none */
  public int find( Predicate<E> P ) {
    for( int i=0; i<_len; i++ )  if( P.test(_es[i]) )  return i;
    return -1;
  }
  /** Find and replace the first matching element using ==.
   *  @param old Element to find
   *  @param nnn Element replacing old
   *  @return true if replacement happened */
  public boolean replace( E old, E nnn ) {
    for( int i=0; i<_len; i++ )  if( _es[i]==old )  { _es[i]=nnn; return true; }
    return false;
  }


  /** Merge-Or.  Merge 2 sorted Arys, tossing out duplicates.  Return a new
   *  sorted Ary with the merged list.  Undefined if the original arrays are
   *  not sorted.  Error if they are not of the same type.  Elements must
   *  implement Comparable.
   *  @param a0 Sorted Ary to merge
   *  @param a1 Sorted Ary to merge
   *  @return A new sorted merged Ary
   */
  public static <X extends Comparable<X>> Ary<X> merge_or( Ary<X> a0, Ary<X> a1 ) {
    int i=0, j=0;
    Ary<X> res = new Ary<>(Arrays.copyOf(a0._es,a0._len+a1._len),0);

    while( i<a0._len && j<a1._len ) {
      X x = a0._es[i];
      X y = a1._es[j];
      int cmp = x.compareTo(y);
      if( cmp<0 )      { res.add(x); i++;      }
      else if( cmp>0 ) { res.add(y);      j++; }
      else             { res.add(x); i++; j++; }
    }
    while( i<a0._len ) res.add(a0._es[i++]);
    while( j<a1._len ) res.add(a1._es[j++]);
    return res;
  }

  /** Merge-Or.  Merge two sorted Arys, tossing out duplicates and elements not
   *  passing the filter.  Return a new sorted Ary with the merged list.
   *  Undefined if the original arrays are not sorted.  Error if they are not
   *  of the same type.  Elements must implement Comparable.
   *  @param a0 Sorted Ary to merge
   *  @param a1 Sorted Ary to merge
   *  @param cmpr Comparator
   *  @param filter Predicate, null means no filter
   *  @return A new sorted merged Ary
   */
  public static <X> Ary<X> merge_or( Ary<X> a0, Ary<X> a1, Comparator<X> cmpr, Predicate<X> filter) {
    int i=0, j=0;
    Ary<X> res = new Ary<>(Arrays.copyOf(a0._es,a0._len+a1._len),0);

    while( i<a0._len && j<a1._len ) {
      X x = a0._es[i];   if( !filter.test(x) ) { i++; continue; }
      X y = a1._es[j];   if( !filter.test(y) ) { j++; continue; }
      int cmp = cmpr.compare(x,y);
      if( cmp<0 )      { res.add(x); i++;      }
      else if( cmp>0 ) { res.add(y);      j++; }
      else             { res.add(x); i++; j++; }
    }
    while( i<a0._len ) if( filter.test(a0._es[i++]) ) res.add(a0._es[i-1]);
    while( j<a1._len ) if( filter.test(a1._es[j++]) ) res.add(a1._es[j-1]);
    return res;
  }


  /** Merge-And.  Merge 2 sorted Arys, keeping only duplicates.  Return a new
   *  sorted Ary with the merged list.  Undefined if the original arrays are
   *  not sorted.  Error if they are not of the same type.  Elements must
   *  implement Comparable.
   *  @param a0 Sorted Ary to merge
   *  @param a1 Sorted Ary to merge
   *  @return A new sorted merged Ary
   */
  public static <X extends Comparable<X>> Ary<X> merge_and( Ary<X> a0, Ary<X> a1 ) {
    int i=0, j=0;
    Ary<X> res = new Ary<>(Arrays.copyOf(a0._es,Math.min(a0._len,a1._len)),0);
    while( i<a0._len && j<a1._len ) {
      X x = a0._es[i];
      X y = a1._es[j];
      int cmp = x.compareTo(y);
      if( cmp<0 )      { i++;      }
      else if( cmp>0 ) {      j++; }
      else { res.add(x); i++; j++; }
    }
    return res;
  }

  /** @return an iterator */
  @Override public Iterator<E> iterator() { return new Iter(); }
  private class Iter implements Iterator<E> {
    int _i=0;
    @Override public boolean hasNext() { return _i<_len; }
    @Override public E next() { return _es[_i++]; }
  }

  @Override public String toString() {
    SB sb = new SB().p('{');
    for( int i=0; i<_len; i++ ) {
      if( i>0 ) sb.p(',');
      if( _es[i] != null ) sb.p(_es[i].toString());
    }
    return sb.p('}').toString();
  }

  private void range_check( int i ) {
    if( i < 0 || i>=_len )
      throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
  }

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof Ary ary) ) return false;
    if( _len != ary._len ) return false;
    if( _es == ary._es ) return true;
    for( int i=0; i<_len; i++ )
      if( !(_es[i]==null ? (ary._es[i] == null) : _es[i].equals(ary._es[i])) )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    int sum=_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i]==null ? 0 : _es[i].hashCode();
    return sum;
  }

  public Ary<E> deepCopy() {
    return new Ary<>(_es.clone(),_len);
  }
}
