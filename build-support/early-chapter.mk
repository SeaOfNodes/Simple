# Chapters 1-3 and 5 previously only had Maven builds.
# Included with the chapter as the working directory.
SHELL := /bin/bash
.DEFAULT_GOAL := tests
.DELETE_ON_ERROR:
SIMPLE := com/seaofnodes/simple
SRC := src/main/java
TST := src/test/java
CLZDIR := build/classes
SEP := $(if $(filter Windows_NT,$(OS)),;,:)
CTAGS ?= ctags
JAVAC_ARGS ?= -g
main_javas := $(wildcard $(SRC)/$(SIMPLE)/*.java $(SRC)/$(SIMPLE)/*/*.java)
test_javas := $(wildcard $(TST)/$(SIMPLE)/*.java $(TST)/$(SIMPLE)/*/*.java)
test_cp := $(patsubst $(TST)/$(SIMPLE)/%.java,com.seaofnodes.simple.%,$(wildcard $(TST)/$(SIMPLE)/*Test.java))
jars := lib/junit-4.12.jar$(SEP)lib/hamcrest-core-1.3.jar
main_classes := $(CLZDIR)/main/.mtag
test_classes := $(CLZDIR)/test/.ttag

$(main_classes): $(main_javas)
	@mkdir -p $(CLZDIR)/main
	javac $(JAVAC_ARGS) -d $(CLZDIR)/main $(main_javas)
	@touch $@

$(test_classes): $(test_javas) $(main_classes) lib/junit-4.12.jar lib/hamcrest-core-1.3.jar
	@mkdir -p $(CLZDIR)/test
	javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -d $(CLZDIR)/test $(test_javas)
	@touch $@

.PHONY: tests tags lib
tests: $(test_classes)
	java -ea -cp "$(CLZDIR)/main$(SEP)$(CLZDIR)/test$(SEP)$(jars)" org.junit.runner.JUnitCore $(test_cp)

tags:
	$(CTAGS) -e --recurse=yes --fields=+fksaiS $(SRC) $(TST)

lib: lib/junit-4.12.jar lib/hamcrest-core-1.3.jar
lib/junit-4.12.jar:
	@mkdir -p lib
	wget -O $@ https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar
lib/hamcrest-core-1.3.jar:
	@mkdir -p lib
	wget -O $@ https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

include ../build-support/chapter-release.mk
