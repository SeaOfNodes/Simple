# Shared printing sources, also usable in a linearized chapter checkout.
PRINT_DIR := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
print_javas := $(wildcard $(PRINT_DIR)/src/main/java/com/seaofnodes/print/*.java)
main_javas += $(print_javas)
