# Common chapter build mechanics. Chapter Makefiles add only their own features.
SHELL := /bin/bash
.DEFAULT_GOAL := default
.DELETE_ON_ERROR:
.NOTINTERMEDIATE:

java_rules := $(lastword $(MAKEFILE_LIST))
BUILD_SUPPORT := $(patsubst %/,%,$(dir $(java_rules)))
SIMPLE := com/seaofnodes/simple
SRC := src/main/java
TST := src/test/java
CLZDIR ?= build/classes
JAVAC_ARGS ?= -g
SEP := $(if $(filter Windows_NT,$(OS)),;,:)
CTAGS := $(shell command -v ctags 2>/dev/null)

include $(BUILD_SUPPORT)/libraries.mk
library_rules := $(lastword $(MAKEFILE_LIST))

# Explicit paths/prerequisites: a missing JAR must not disappear from the classpath.
space := $() $()
libs := $(required_jars)
jars := $(subst $(space),$(SEP),$(libs))
main_javas := $(wildcard $(SRC)/$(SIMPLE)/*.java $(SRC)/$(SIMPLE)/*/*.java $(SRC)/$(SIMPLE)/node/cpus/*/*.java)
test_javas := $(wildcard $(TST)/$(SIMPLE)/*.java $(TST)/$(SIMPLE)/*/*.java)
include $(firstword $(wildcard ../graph/graph.mk graph/graph.mk))
include $(firstword $(wildcard ../print/print.mk print/print.mk))
ifeq ($(USE_ISA),1)
include $(firstword $(wildcard ../isa/isa.mk isa/isa.mk))
endif

main_classes := $(CLZDIR)/main/.mtag
test_classes := $(CLZDIR)/test/.ttag
simple_driver := $(TST)/$(SIMPLE)/Simple.java
test_cp = $(patsubst $(TST)/$(SIMPLE)/%.java,com.seaofnodes.simple.%,$(wildcard $(TST)/$(SIMPLE)/*Test.java)) $(EXTRA_TESTS)
JVM = nice java -ea -cp "$(CLZDIR)/main$(SEP)$(jars)$(SEP)$(CLZDIR)/test"

.PHONY: default build tests fuzzer tags clean release
DEFAULT_TARGETS ?= tests
default: $(DEFAULT_TARGETS) $(if $(CTAGS),tags)
build: $(main_classes)
print-%: ; @echo $* = $($*)

# Compile a complete source set once, including shared sources. Per-class rules
# and collecting only changed files left stale dependents in several chapters.
$(CLZDIR)/main $(CLZDIR)/test:
	@mkdir -p $@

$(main_classes): $(main_javas) $(required_jars) Makefile $(java_rules) $(library_rules) | $(CLZDIR)/main
	$(file >$(CLZDIR)/main.sources,$(main_javas))
	@echo "compiling main " $(words $(main_javas)) " files"
	@if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main @$(CLZDIR)/main.sources >$(CLZDIR)/main.javac.log 2>&1; then cat $(CLZDIR)/main.javac.log; exit 1; fi
	@touch $@

$(test_classes): $(test_javas) $(isa_test_javas) $(main_classes) $(required_jars) Makefile $(java_rules) $(library_rules) | $(CLZDIR)/test
	$(file >$(CLZDIR)/test.sources,$(test_javas) $(isa_test_javas))
	@echo "compiling test " $(words $(test_javas) $(isa_test_javas)) " files"
	@if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test @$(CLZDIR)/test.sources >$(CLZDIR)/test.javac.log 2>&1; then cat $(CLZDIR)/test.javac.log; exit 1; fi
	@touch $@

ifneq ($(CUSTOM_TESTS),1)
tests: $(test_classes)
	@mkdir -p build/objs
	@$(JVM) org.junit.runner.JUnitCore $(test_cp)
ifneq ($(wildcard $(TST)/$(SIMPLE)/FuzzerWrap.java),)
tests: fuzzer
endif
endif

ifneq ($(wildcard $(TST)/$(SIMPLE)/FuzzerWrap.java),)
fuzzer: $(test_classes)
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap
endif

ifneq ($(CUSTOM_RELEASE),1)
include $(BUILD_SUPPORT)/chapter-release.mk
endif

tags: TAGS
TAGS: $(main_javas) $(test_javas)
	@$(CTAGS) -e --recurse=yes --fields=+fksaiS $(SRC) $(TST)

clean:
	rm -rf build
	rm -f TAGS
