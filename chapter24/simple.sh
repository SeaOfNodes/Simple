#! /bin/sh
java -ea -cp "build/classes/test:build/classes/main:lib/hamcrest-core-1.3.jar:lib/junit-4.12.jar" com.seaofnodes.simple.Simple $@
