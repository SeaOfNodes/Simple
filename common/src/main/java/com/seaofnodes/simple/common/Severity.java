package com.seaofnodes.simple.common;


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
