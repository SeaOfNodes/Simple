# Include after defining chapter source variables. Works in linearized checkouts too.
GRAPH_DIR := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
graph_javas := $(wildcard $(GRAPH_DIR)/src/main/java/com/seaofnodes/graph/*.java)
main_javas += $(graph_javas)

# Defining view here must not change the chapter's default target.
graph_goal := $(.DEFAULT_GOAL)
.PHONY: view
view: build
	java -ea -cp "$(CLZDIR)/main" com.seaofnodes.simple.print.SimpleGraphObserver
.DEFAULT_GOAL := $(graph_goal)
