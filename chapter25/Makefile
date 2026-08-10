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
# These tests do not rely on the stdlib
test_alone := com.seaofnodes.simple.BrainFuckTest com.seaofnodes.simple.MergeSortTest com.seaofnodes.simple.TypeTest
test_raw_nums0 := 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21
test_raw_nums1 := 22 23 24
# These tests DO rely on the stdlib
test_raw_sys   := 25

# Convert chapter numbers to test files
test_raw0    := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_nums0))
test_raw1    := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_nums1))
test_sys     := $(patsubst %,com.seaofnodes.simple.Chapter%Test,$(test_raw_sys)  )
# All the 'src/test/java' files ending in 'Test'
test_cp0     := $(patsubst $(TST)/$(SIMPLE)/%.java,com.seaofnodes.simple.%,$(wildcard $(TST)/$(SIMPLE)/*Test.java))
# Minus raw0, raw1, alone tests
test_cp1     := $(filter-out $(test_raw0) ,$(test_cp0))
test_cp2     := $(filter-out $(test_raw1) ,$(test_cp1))
test_cp3     := $(filter-out $(test_alone),$(test_cp2))
test_cp4     := $(filter-out com.seaofnodes.simple.Chapter25Test,$(test_cp3))
test_cp      := $(filter-out $(test_sys)  ,$(test_cp4))
# All the classes to compile
classes = $(main_classes) $(test_classes)
# All the libraries
libs = $(wildcard lib/*jar)
jars = $(subst $(space),$(SEP),$(libs))


default_targets := TAGS release tests
# Optionally add ctags to the default target if a reasonable one was found.
ifneq ($(CTAGS),)
default_targets += tags
endif

default: $(default_targets)

#######################################################
# Compile Java as a whole source set.  javac tracks Java dependencies
# internally, so compiling only the directly out-of-date .java files can leave
# stale dependent .class files behind.
$(CLZDIR)/main/.mtag: $(main_javas)
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@# This crap is really just "javac", but with a beautiful error message.
	@# The very very long list of java files is suppressed and counted.
	@# The required output "Note: blah blah deprecated blah" is suppressed.
	$(file > .argsM.txt, $(main_javas))
	@echo "compiling main " `wc -w < .argsM.txt` " files"
	@if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main @.argsM.txt >& .out.txt ; then \
            cat .out.txt ; \
            exit 1; \
	fi
	@rm -rf .out.txt
	@touch $(CLZDIR)/main/.mtag
	@rm -f .argsM.txt


#######################################################

$(CLZDIR)/test/.ttag: $(test_javas) $(CLZDIR)/main/.mtag
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	$(file > .argsT.txt, $(test_javas))
	@echo "compiling test " `wc -w < .argsT.txt` " files"
	@if ! javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test @.argsT.txt >& .out.txt ; then \
            cat .out.txt ; \
            exit 1; \
	fi
	@rm -rf .out.txt
	@touch $(CLZDIR)/test/.ttag
	@rm -f .argsT.txt

#######################################################

# Default Simple .o target is build/objs which is where all the test .o's get
# dumped.  The default sys search needs to NOT include all the dumped test
# files; move it down into lib/
OBJS:= build/objs
SYS_SMPS:= $(wildcard src/main/smp/*.smp src/main/smp/*/*.smp)
CPU_PORT:= x86_64_v2
CALL_CONV:= win64
CPU_ABI:= $(CPU_PORT)_$(CALL_CONV)
RELEASE:= build/release
RELEASE_SYS:= $(RELEASE)/$(CPU_ABI)/sys.o

$(OBJS)/lib_$(CPU_ABI)/sys.o:	$(CLZDIR)/main/.mtag $(SYS_SMPS)
	@echo "simping " $@
	@$(JVM) com.seaofnodes.simple.Simple --cpu $(CPU_PORT) --abi $(CALL_CONV) --norun -o $@ src/main/smp/sys.smp

$(RELEASE_SYS):	$(OBJS)/lib_$(CPU_ABI)/sys.o
	@echo "copying " $@
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@cp $< $@

#######################################################
# Base launch line for JVM tests
JVM=nice java -ea -cp "$(CLZDIR)/main${SEP}${jars}${SEP}$(CLZDIR)/test"

# Tests that do NOT need sys.smp
tests_raw0:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "testing " $(test_raw0)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_raw0)

tests_raw1:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "testing " $(test_raw1)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_raw1)

# Larger standalone tests, still JUnits, still no sys.smp
tests_alone:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "testing " $(test_alone)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_alone)

# Tests that DO need sys.smp
tests_sys:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_SYS)
	@echo "testing " $(test_sys)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_sys)

fuzzer: $(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "fuzzing "
	@$(JVM) org.junit.runner.JUnitCore com.seaofnodes.simple.FuzzerWrap

tests:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_SYS) tests_raw0 tests_raw1 tests_alone tests_sys
	@echo "All tests"
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(JVM) org.junit.runner.JUnitCore $(test_cp)


# Build a Simple jar and release stdlib
release:	build/release/Simple.jar $(RELEASE_SYS)

# Build a Simple jar
build/release/Simple.jar:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "jarring " $@ " because " $?
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@printf "Main-Class: com.seaofnodes.simple.Simple\nClass-Path: ../../lib/junit-4.12.jar ../../lib/hamcrest-core-1.3.jar\n\n" > build/release/MANIFEST.MF
	@jar cfm $@ build/release/MANIFEST.MF -C $(CLZDIR)/main . -C $(CLZDIR)/test . -C $(SRC)/$(SIMPLE) . -C $(TST)/$(SIMPLE) .

# Launch viewer
view:	$(main_classes)
	@echo "viewing "
	@$(JVM) com.seaofnodes.simple.JSViewer

#######################################################
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
TAGS:	$(main_javas) $(test_javas)
	@rm -f TAGS
	@$(CTAGS) -e --recurse=yes --fields=+fksaiS $(SRC) $(TST)
