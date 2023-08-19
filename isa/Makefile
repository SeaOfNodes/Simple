SHELL := /bin/bash
.DEFAULT_GOAL := tests
.DELETE_ON_ERROR:
main_javas := $(wildcard src/main/java/com/seaofnodes/isa/*.java)
test_javas := $(wildcard src/test/java/com/seaofnodes/isa/*.java)
eval_javas := $(wildcard src/test-support/java/com/seaofnodes/isa/eval/*.java)

.PHONY: tests
tests: build/classes/.tag
	java -ea -cp build/classes com.seaofnodes.isa.X86Test
	java -ea -cp build/classes com.seaofnodes.isa.RiscTest

build/classes/.tag: $(main_javas) $(test_javas) $(eval_javas)
	@mkdir -p build/classes
	javac -d build/classes $(main_javas) $(test_javas) $(eval_javas)
	@touch $@
