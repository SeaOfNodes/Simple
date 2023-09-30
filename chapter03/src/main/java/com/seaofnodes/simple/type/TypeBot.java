package com.seaofnodes.simple.type;

import java.lang.StringBuilder;

public class TypeBot extends Type {

    public static final TypeBot BOTTOM = new TypeBot();
  
    @Override
    public String toString() { return "Bottom"; }
  
    @Override 
    public StringBuilder _print(StringBuilder sb) { return sb.append(toString()); }
}
