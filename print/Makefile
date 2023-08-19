SHELL := /bin/bash
.DEFAULT_GOAL := tests
.DELETE_ON_ERROR:
main_javas := $(wildcard src/main/java/com/seaofnodes/print/*.java)
test_javas := $(wildcard src/test/java/com/seaofnodes/print/*.java)

.PHONY: tests
tests: build/classes/.tag
	java -ea -cp build/classes com.seaofnodes.print.PrinterTest

build/classes/.tag: $(main_javas) $(test_javas)
	@mkdir -p build/classes
	javac -d build/classes $(main_javas) $(test_javas)
	@touch $@
