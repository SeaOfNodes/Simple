# Build from this chapter directory; make libs/init prepares the offline cache.
USE_ISA := 1
CUSTOM_TESTS := 1
CUSTOM_RELEASE := 1
DEFAULT_TARGETS := release tests
include $(firstword $(wildcard ../build-support/java.mk build-support/java.mk))

# These tests do not rely on the stdlib
test_alone := com.seaofnodes.simple.BrainFuckTest com.seaofnodes.simple.MergeSortTest com.seaofnodes.simple.TypeTest
test_raw_nums0 := 01 02 03 04 05 06 07 08 09 10 12 13 14Float 14 15 16 17a 17b 18 19 20 21
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

# Measured groups keep class fixtures together. New tests join group 7 rather
# than silently falling out of the suite. Each group has private native outputs.
test_group0 := com.seaofnodes.simple.DrawTest
test_group1 := com.seaofnodes.simple.GraphicsTest
test_group2 := $(addprefix com.seaofnodes.simple.,TimeTest MemoryEncodingTest ChainedComparisonTest ParserTest Chapter28bTest)
test_group3 := $(addprefix com.seaofnodes.simple.,StringFormatTest StaticDataTest PhaseTest FinalConstructionTest PartialReadTest EscapeFlowTest CallEndTest BulkMemPhiTest)
test_group4 := $(addprefix com.seaofnodes.simple.,BreakoutTest ConvertNodeTest ArithNodeTest MethodAccessTest Int32Test MinusNodeTest ConstructorCompletionTest)
test_group5 := $(addprefix com.seaofnodes.simple.,TerminalInputTest ParserDiagnosticTest ArrayInitTest MethodFieldsTest ConstantArrayTest BoundaryTest IntegerLiteralTest ConstructionRequirementsTest)
test_group6 := $(addprefix com.seaofnodes.simple.,AssemblyDumpTest AudioTest MathTest TicTacToeTest ModTest SelectiveLoadingTest FloatComparisonNativeTest SchedulingTest SeparateCompilationTest ConstructionGroupTest CompoundAssignmentTest)
test_group7 := $(filter-out $(test_group0) $(test_group1) $(test_group2) $(test_group3) $(test_group4) $(test_group5) $(test_group6),$(test_cp))
test_groups := $(addprefix tests_group,0 1 2 3 4 5 6 7)

# Fast compiler checks retain the full lattice, chapter/backend regressions,
# fuzzer regression seeds, and focused compiler tests. Library/application and
# CLI integration remains in `tests`. New compiler tests join sanity group 2.
sanity_omit := $(addprefix com.seaofnodes.simple.,DrawTest GraphicsTest TimeTest StringFormatTest BreakoutTest TerminalInputTest AudioTest MathTest TicTacToeTest StreamIOTest RuntimeArgsTest NativeExecutionTest ParserDiagnosticTest)
sanity_cp := $(filter-out $(sanity_omit),$(test_cp))
sanity_group0 := $(addprefix com.seaofnodes.simple.,ClassInitDependenciesTest ConvertNodeTest ArithNodeTest MemoryEncodingTest ModTest IntrinsicTest AssemblyDumpTest)
sanity_group1 := $(addprefix com.seaofnodes.simple.,StaticDataTest FloatComparisonNativeTest ArrayInitTest ConstantArrayTest BoundaryTest Int32Test ReceiverTest GlobalAssignmentTest IncrementalBuildTest SeparateCompilationTest)
sanity_group2 := $(filter-out $(sanity_group0) $(sanity_group1),$(sanity_cp))
sanity_groups := $(addprefix sanity_group,0 1 2)

# Size each test JVM for a share of the machine, instead of giving every JVM
# JIT and GC pools sized for all cores. Absolute paths also serve child JVMs
# launched from a group's private working directory (native Java on Windows).
TEST_JAVA_OPTS ?= -XX:ActiveProcessorCount=2
test_path = $(if $(filter Windows_NT,$(OS)),$(shell cygpath -m "$(abspath $(1))"),$(abspath $(1)))
test_classpath := $(subst $(space),$(SEP),$(foreach path,$(CLZDIR)/main $(libs) $(CLZDIR)/test,$(call test_path,$(path))))
TEST_JVM = nice java -ea $(TEST_JAVA_OPTS) -cp "$(test_classpath)"
fuzzer: private JVM = $(TEST_JVM)

# Default Simple .o target is build/objs which is where all the test .o's get
# dumped.  The default sys search needs to NOT include all the dumped test
# files; move it down into lib/
OBJS:= build/objs
SYS_SMPS:= $(wildcard src/main/smp/*.smp src/main/smp/*/*.smp src/main/smp/*/*/*.smp)
CPU_PORT:= x86_64_v2
CALL_CONV:= $(if $(filter Windows_NT,$(OS)),win64,SystemV)
CPU_ABI:= $(CPU_PORT)_$(CALL_CONV)
RELEASE:= build/release
TEST_SYS:= $(OBJS)/lib_$(CPU_ABI)/sys.o
RELEASE_SYS:= $(RELEASE)/$(CPU_ABI)/sys.o
RELEASE_CRT:= $(RELEASE)/$(CPU_ABI)/simple_crt.o
RELEASE_GRAPHICS:= $(RELEASE)/$(CPU_ABI)/simple_graphics.o
RELEASE_AUDIO:= $(RELEASE)/$(CPU_ABI)/simple_audio.o
RELEASE_TIME:= $(RELEASE)/$(CPU_ABI)/simple_time.o
RELEASE_ARGS:= $(RELEASE)/$(CPU_ABI)/simple_args.o
C_CALL_CONV:= $(if $(filter win64,$(CALL_CONV)),ms_abi,sysv_abi)
GAME_DIR:= build/examples/$(CPU_ABI)
TICTACTOE:= $(GAME_DIR)/TicTacToe$(if $(filter Windows_NT,$(OS)),.exe,)

# Build a runnable game without starting it. Native outputs are ABI-specific;
# WSL can use CLZDIR=build/classes-linux to keep its Java build separate too.
.PHONY: tictactoe
tictactoe: $(TICTACTOE)

$(TICTACTOE): docs/examples/TicTacToe.smp $(CLZDIR)/test/.ttag $(RELEASE_SYS) $(RELEASE_CRT) Makefile
	@echo "building " $@
	@$(JVM) com.seaofnodes.simple.Simple --link --cpu $(CPU_PORT) --abi $(CALL_CONV) -L $(dir $(RELEASE_SYS)) -o $(GAME_DIR)/TicTacToe.o $<

$(TEST_SYS):	$(CLZDIR)/main/.mtag $(simple_driver) $(SYS_SMPS) | $(CLZDIR)/test/.ttag
	@echo "simping " $@
	@$(JVM) com.seaofnodes.simple.Simple --cpu $(CPU_PORT) --abi $(CALL_CONV) --norun -o $@ src/main/smp/sys.smp

$(RELEASE_SYS):	$(OBJS)/lib_$(CPU_ABI)/sys.o
	@echo "copying " $@
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@cp $< $@
	@cp -r $(dir $<)sys $(dir $@)

$(RELEASE_ARGS): src/main/smp/sys/rt/simple_args.smp $(CLZDIR)/main/.mtag $(simple_driver) Makefile | $(CLZDIR)/test/.ttag
	@echo "simping " $@
	@$(JVM) com.seaofnodes.simple.Simple --cpu $(CPU_PORT) --abi $(CALL_CONV) --norun -o $@ $<

$(RELEASE_CRT): src/main/c/simple_crt.c $(RELEASE_ARGS) $(RELEASE_GRAPHICS) $(RELEASE_AUDIO) $(RELEASE_TIME) Makefile
	@echo "compiling " $@
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@gcc -c -O2 -DCALL_CONV=$(C_CALL_CONV) -o $@ $<

$(RELEASE_GRAPHICS): src/main/c/simple_graphics.c src/main/c/simple_graphics.h Makefile
	@echo "compiling " $@
	@mkdir -p $(dir $@)
	@gcc -c -O2 -Wall -Wextra -o $@ $<

# Optional native graphics backend. Nothing in the default build depends on it.
include graphics.mk
include audio.mk

$(RELEASE_AUDIO): src/main/c/simple_audio.c src/main/c/simple_audio.h Makefile
	@mkdir -p $(dir $@)
	@gcc -c -O2 -Wall -Wextra -o $@ $<

$(RELEASE_TIME): src/main/c/simple_time.c Makefile
	@mkdir -p $(dir $@)
	@gcc -c -O2 -Wall -Wextra -o $@ $<

.PHONY: tests_nographics
tests_nographics:
	@SIMPLE_MINIFB=off SIMPLE_MINIAUDIO=off $(MAKE) tests

#######################################################
# Base launch line for JVM tests
.PHONY: tests_raw0 tests_raw1 tests_alone tests_programs tests_sys $(test_groups) tests_lattice0 tests_lattice1 tests_lattice2 tests_lattice3

# Tests that do NOT need sys.smp
tests_raw0:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_CRT)
	@echo "testing " $(test_raw0)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(TEST_JVM) org.junit.runner.JUnitCore $(test_raw0)

tests_raw1:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag $(RELEASE_CRT)
	@echo "testing " $(test_raw1)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(TEST_JVM) org.junit.runner.JUnitCore $(test_raw1)

# Larger regressions, including BrainFuck's standard-library buffer variant.
tests_alone: tests_programs tests_lattice0 tests_lattice1 tests_lattice2 tests_lattice3
tests_programs: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@echo "testing " $(filter-out com.seaofnodes.simple.TypeTest,$(test_alone))
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(TEST_JVM) org.junit.runner.JUnitCore $(filter-out com.seaofnodes.simple.TypeTest,$(test_alone))

# Partition the lattice's outer type loop across processes: type interning and
# cyclic traversal scratch state are shared mutable data within a JVM. Part 0
# runs the other TypeTest methods too; direct JUnit/IDE runs still check it all.
tests_lattice0: $(test_classes)
	@echo "testing lattice part 0/4 and other type checks"
	@$(TEST_JVM) -Dsimple.lattice.parts=4 -Dsimple.lattice.part=0 org.junit.runner.JUnitCore com.seaofnodes.simple.TypeTest
tests_lattice1 tests_lattice2 tests_lattice3: tests_lattice%: $(test_classes)
	@echo "testing lattice part $*/4"
	@$(TEST_JVM) -Dsimple.lattice.parts=4 -Dsimple.lattice.part=$* com.seaofnodes.simple.TestRunner TypeTest testLatticeTheory

# Tests that DO need sys.smp
tests_sys: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@echo "testing " $(test_sys)
	@[ -d $(OBJS) ] || mkdir -p $(OBJS)
	@$(TEST_JVM) org.junit.runner.JUnitCore $(test_sys)

$(test_groups): tests_group%: $(test_classes) $(TEST_SYS) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(TEST_JVM) com.seaofnodes.simple.TestWorkspace $@ $(CPU_ABI)
	@echo "testing $@: $(test_group$*)"
	@cd build/tests/$@ && $(TEST_JVM) org.junit.runner.JUnitCore $(test_group$*)

.PHONY: sanity $(sanity_groups)
$(sanity_groups): sanity_group%: $(test_classes) $(TEST_SYS) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(TEST_JVM) com.seaofnodes.simple.TestWorkspace $@ $(CPU_ABI)
	@echo "testing $@: $(sanity_group$*)"
	@cd build/tests/$@ && $(TEST_JVM) org.junit.runner.JUnitCore $(sanity_group$*)

sanity: $(sanity_groups) tests_raw0 tests_raw1 fuzzer tests_lattice0 tests_lattice1 tests_lattice2 tests_lattice3
	@echo "All sanity checks passed"

# One test method (or the whole class when METHOD is omitted).
CLASS ?= Chapter25Test
METHOD ?=
.PHONY: test
test: $(main_classes) $(test_classes) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(TEST_JVM) com.seaofnodes.simple.TestRunner "$(CLASS)" "$(METHOD)"

# At -j8, start the remaining chapter cohorts before the short lattice parts
# so their native links overlap the earlier groups instead of forming a tail.
tests: $(test_groups) tests_sys fuzzer tests_raw1 tests_raw0 tests_alone
	@echo "All tests passed"


# Build a Simple jar and release stdlib
release:	build/release/Simple.jar $(RELEASE_SYS) $(RELEASE_CRT)

# Build a Simple jar
build/release/Simple.jar:	$(CLZDIR)/main/.mtag $(CLZDIR)/test/.ttag
	@echo "jarring " $@ " because " $?
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@printf "Main-Class: com.seaofnodes.simple.Simple\nClass-Path: ../../lib/junit-4.12.jar ../../lib/hamcrest-core-1.3.jar\n\n" > build/release/MANIFEST.MF
	@jar cfm $@ build/release/MANIFEST.MF -C $(CLZDIR)/main . -C $(CLZDIR)/test . -C $(SRC)/$(SIMPLE) . -C $(TST)/$(SIMPLE) .



.PHONY: spill-stats
spill-stats: $(test_classes) $(TEST_SYS) $(RELEASE_SYS) $(RELEASE_CRT)
	@$(JVM) com.seaofnodes.simple.SpillStats
