package com.seaofnodes.simple.type;

public class TypeControl extends Type {

    public static final TypeControl CONTROL = new TypeControl();

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(toString());
    }

    @Override
    public String toString() {
        return "Control";
    }
}
