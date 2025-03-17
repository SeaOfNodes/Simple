package com.seaofnodes.simple.codegen;

// This Node rip-relative encodings has sizes that vary by delta
public interface RIPRelSize {
    byte encSize(int delta);
}
