package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeField;

public class MemProjNode extends ProjNode {

    TypeField _fieldType;

    public MemProjNode(MultiNode ctrl, int idx, String label, TypeField fieldType) {
        super(ctrl, idx, label);
        _fieldType = fieldType;
    }
}
