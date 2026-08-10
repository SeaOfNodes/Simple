package com.seaofnodes.simple;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;

/** Make's single-class/single-method JUnit entry point. */
public class TestRunner {
    public static void main(String[] args) throws ClassNotFoundException {
        String name=args[0];
        Class<?> test=Class.forName(name.contains(".") ? name : "com.seaofnodes.simple."+name);
        Request request=args[1].isEmpty() ? Request.aClass(test) : Request.method(test,args[1]);
        JUnitCore runner=new JUnitCore();
        runner.addListener(new TextListener(System.out));
        System.exit(runner.run(request).wasSuccessful() ? 0 : 1);
    }
}
