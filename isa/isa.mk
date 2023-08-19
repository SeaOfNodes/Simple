# Shared instruction encoders; works in both chapter and linearized checkouts.
ISA_DIR := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
isa_javas := $(wildcard $(ISA_DIR)/src/main/java/com/seaofnodes/isa/*.java)
main_javas += $(isa_javas)
isa_test_javas := $(wildcard $(ISA_DIR)/src/test-support/java/com/seaofnodes/isa/eval/*.java)
