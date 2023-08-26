/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is
 * The Ecstasy Project (https://github.com/xtclang/).
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
package com.seaofnodes.simple.lexer.ecstasy;


/**
 * Severity levels.
 */
public enum Severity
    {
    NONE, INFO, WARNING, ERROR, FATAL;

    public String desc()
        {
        String sName = name();
        StringBuilder sb = new StringBuilder(sName.length());
        sb.append(sName.charAt(0));
        sb.append(sName.substring(1).toLowerCase());
        return sb.toString();
        }
    }
