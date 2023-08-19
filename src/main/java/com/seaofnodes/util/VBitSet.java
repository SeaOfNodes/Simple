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

import java.util.BitSet;

public class VBitSet extends BitSet {
  // Cannot override 'set' to return a value... :-P
  public boolean tset(int idx) { boolean b = get(idx); set(idx); return b; }
  public boolean test(int idx) { return get(idx); }
  public VBitSet clr() { clear(); return this; }
}
