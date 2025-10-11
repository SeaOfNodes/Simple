#
# Makefile expects to run at the project root, and not nested inside anywhere
#
#  cd $DESK/Simple/chapterXX; make
#
#
# first time only - this is the only thing that hits the Interwebs
#
#   make lib
#
# any time you want to nuke everything from space:
#
#   make clean tags
#
# run the tests
#
#   make tests

#######################################################
#
# Boilerplate because make syntax isn't the best
#

# Use bash for recipes
SHELL := /bin/bash

# Remove target on error (no broken leftover files)
.DELETE_ON_ERROR:

# Keep partial builds but not partial recipes; keeping them speeds up the next `make`
.NOTINTERMEDIATE:

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


default_targets := $(CLZDIR)/.tag $(test_classes)
# Optionally add ctags to the default target if a reasonable one was found.
ifneq ($(CTAGS),)
default_targets += tags
endif

default: $(default_targets)

#######################################################
# Compile just the out-of-date files

OODM :=
$(CLZDIR)/main/.mtag: $(main_classes) $(main_javas)
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@# This crap is really just "javac", but with a beautiful error message.
	@# The very very long list of java files is suppressed and counted.
	@# The required output "Note: blah blah deprecated blah" is suppressed.
	$(file > .argsM.txt , $(OODM))
	@if [ ! -z "$(OODM)" ] ; then \
	  echo "compiling main because " $< " and " `wc -w < .argsM.txt` " more files" ; \
	  if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main $(OODM) >& .out.txt ; then \
            cat .out.txt ; \
            exit 1; \
          fi ; \
          rm -rf .out.txt ; \
	fi
	@touch $(CLZDIR)/main/.mtag
	@rm -f .argsM.txt

# Collect just the out-of-date files
$(main_classes): $(CLZDIR)/main/%class: $(SRC)/%java
	$(eval OODM += $$<)


#######################################################

OODT :=
$(CLZDIR)/test/.ttag: $(test_classes) $(test_javas) $(CLZDIR)/main/.mtag
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	$(file > .argsT.txt $(OODT))
	@if [ ! -z "$(OODT)" ] ; then \
	  echo "compiling test because " $< " and " `wc -w < .argsT.txt` " more files" ; \
	  if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test $(OODT) >& .out.txt ; then \
            cat .out.txt ; \
            exit 1; \
          fi ; \
          rm -rf .out.txt ; \
	fi
	@touch $(CLZDIR)/test/.ttag
	@rm -f .argsT.txt

# Collect just the out-of-date files
$(test_classes): $(CLZDIR)/test/%class: $(TST)/%java
	$(eval OODT += $$<)

#######################################################
# Base launch line for JVM tests
JVM=nice java -ea -cp "$(CLZDIR)/main${SEP}${jars}${SEP}$(CLZDIR)/test"


tests:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "testing " $(test_cp)
	@[ -d build/objs ] || mkdir -p build/objs
	@$(JVM) org.junit.runner.JUnitCore $(test_cp)
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap

fuzzer: $(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "fuzzing " $(test_cp)
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap

# Build a Simple jar
release:	build/release/simple.jar

# Build a Simple jar
build/release/simple.jar:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
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
