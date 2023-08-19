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

import org.jctools.maps.NonBlockingHashMap;

import java.util.Set;

@SuppressWarnings("unchecked")
public class IHashMap {
  private final NonBlockingHashMap _map = new NonBlockingHashMap();
  public <T> T put(T kv) { _map.put(kv,kv); return kv; }
  public <T> T put(T k, T v) { _map.put(k,v); return v; }
  public <T> T get(T key) { return (T)_map.get(key); }
  public void remove(Object key) { _map.remove(key); }
  public void clear() { _map.clear(); }
  public boolean isEmpty() { return _map.isEmpty(); }
  public <T> Set<T> keySet() { return _map.keySet(); }
}
