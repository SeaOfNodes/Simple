SHELL := /bin/bash
.DELETE_ON_ERROR:


# for printing variable values
# usage: make print-VARIABLE
#        > VARIABLE = value_of_variable
print-%  : ; @echo $* = $($*)

# literal space
space := $() $()

# Decide OS-specific questions
# jar-file seperator
ifeq ($(OS),Windows_NT)
	SEP = ;
else
	SEP = :
endif
# Find a reasonable ctags.
CTAGS = $(shell which ctags)
# Hack for MacOS: /usr/bin/ctags is unfriendly, so look for ctags from brew
ifeq ($(UNAME),Darwin)
	CTAGS = $(shell brew list ctags 2> /dev/null | grep bin/ctags)
endif

# Fun Args to javac.  Mostly limit to java8 source definitions, and fairly
# aggressive lint warnings.
JAVAC_ARGS = -g

# Source code
SIMPLE := com/seaofnodes/simple
SRC := src/main/java
TST := src/test/java
CLZDIR:= build/classes
main_javas   := $(wildcard $(SRC)/$(SIMPLE)/*java $(SRC)/$(SIMPLE)/*/*java $(SRC)/$(SIMPLE)/node/cpus/*/*java)
test_javas   := $(wildcard $(TST)/$(SIMPLE)/*java $(TST)/$(SIMPLE)/*/*java)
main_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_javas))
test_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_javas))
test_cp      := $(patsubst $(TST)/$(SIMPLE)/%.java,com.seaofnodes.simple.%,$(wildcard $(TST)/$(SIMPLE)/*Test.java))
classes = $(main_classes) $(test_classes)
# All the libraries
libs = $(wildcard lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))


default_targets := $(main_classes) $(test_classes)
# Optionally add ctags to the default target if a reasonable one was found.
ifneq ($(CTAGS),)
default_targets += tags
endif

default: $(default_targets)

# Compile just the out-of-date files
$(main_classes): $(CLZDIR)/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main $(main_javas)

$(test_classes): $(CLZDIR)/test/%class: $(TST)/%java $(main_classes)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test $(test_javas)

# Base launch line for JVM tests
JVM=nice java -ea -cp "$(CLZDIR)/main${SEP}${jars}${SEP}$(CLZDIR)/test"

tests:	$(default_targets)
	@echo "testing " $(test_cp)
	@[ -d build/objs ] || mkdir -p build/objs
	@$(JVM) org.junit.runner.JUnitCore $(test_cp)
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap

fuzzer: $(default_targets)
	@echo "fuzzing " $(test_cp)
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap

# Build a Simple jar
release:	build/release/simple.jar

# Build a Simple jar
build/release/simple.jar:	$(main_classes) $(test_classes)
	@echo "jarring " $@ " because " $?
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@jar cf build/release/simple.jar -C $(CLZDIR)/main . -C $(CLZDIR)/test . -C $(SRC)/$(SIMPLE) . -C $(TST)/$(SIMPLE) .

# Launch viewer
view:	$(main_classes)
	@echo "viewing "
	@$(JVM) com.seaofnodes.simple.JSViewer

.PHONY: clean
clean:
	rm -rf build
	rm -f TAGS
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)

# Download libs from maven
lib:	lib/junit-4.12.jar  lib/hamcrest-core-1.3.jar

# Jars
lib/junit-4.12.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar)

lib/hamcrest-core-1.3.jar:
	@[ -d lib ] || mkdir -p lib
	@(cd lib; wget https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar)

# Build emacs tags (part of a tasty emacs ide experience)
tags:	$(main_javas) $(test_javas)
	@rm -f TAGS
	@$(CTAGS) -e --recurse=yes --extras=+q --fields=+fksaiS $(SRC) $(TST)
