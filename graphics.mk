# Optional MiniFB build, pinned independently of Simple's default build.
# A C-only GDI/X11 subset: no OpenGL, C++ wrapper, examples, or CMake required.
MINIFB_REV := f92be8e966f194004853c10196e704f4510f58d4
MINIFB_SRC := lib/minifb-$(MINIFB_REV)
MINIFB_ARCHIVE := lib/minifb-$(MINIFB_REV).tar.gz
MINIFB_STAMP := $(MINIFB_SRC)/.simple-$(MINIFB_REV)
MINIFB_COMMON := MiniFB_common MiniFB_cpu_x86 MiniFB_internal MiniFB_stretch \
    MiniFB_stretch_sse2 MiniFB_stretch_avx2 MiniFB_stretch_neon MiniFB_timer MiniFB_utf8
ifeq ($(OS),Windows_NT)
    GRAPHICS_CC := x86_64-w64-mingw32-gcc
    GRAPHICS_LIB := $(RELEASE)/$(CPU_ABI)/simple_minifb.dll
    MINIFB_PLATFORM := windows/WinMiniFB
    MINIFB_LIBS := -lgdi32 -luser32 -lwinmm
    MINIFB_FLAGS := -D_WIN32_WINNT=0x0601
else
    GRAPHICS_CC := gcc
    GRAPHICS_LIB := $(RELEASE)/$(CPU_ABI)/libsimple_minifb.so
    MINIFB_PLATFORM := x11/X11MiniFB x11/X11MiniFB_scale_funcs x11/X11MiniFB_text \
        MiniFB_linux MiniFB_evdev_keys
    MINIFB_LIBS := -lX11 -lm
    MINIFB_FLAGS := -fPIC -D_POSIX_C_SOURCE=200809L
endif
MINIFB_SOURCES = $(addprefix $(MINIFB_SRC)/src/,$(addsuffix .c,$(MINIFB_COMMON) $(MINIFB_PLATFORM)))
GRAPHICS_EXAMPLE := $(GAME_DIR)/Graphics$(if $(filter Windows_NT,$(OS)),.exe,)
GRAPHICAL_TICTACTOE := $(GAME_DIR)/TicTacToeGraphical$(if $(filter Windows_NT,$(OS)),.exe,)

.PHONY: graphics graphics-example run-graphics
.PHONY: graphical-tictactoe run-graphical-tictactoe
graphical-tictactoe: $(GRAPHICAL_TICTACTOE)
$(GRAPHICAL_TICTACTOE): docs/examples/TicTacToeGraphical.smp $(CLZDIR)/test/.ttag $(RELEASE_SYS) $(RELEASE_CRT) graphics.mk
	@$(JVM) com.seaofnodes.simple.Simple --link --cpu $(CPU_PORT) --abi $(CALL_CONV) -L $(dir $(RELEASE_SYS)) -o $(GAME_DIR)/TicTacToeGraphical.o $<

run-graphical-tictactoe: graphics audio graphical-tictactoe
	SIMPLE_MINIFB="$(abspath $(GRAPHICS_LIB))" SIMPLE_MINIAUDIO="$(abspath $(AUDIO_LIB))" $(GRAPHICAL_TICTACTOE)
graphics: $(GRAPHICS_LIB)

lib libs init: $(MINIFB_STAMP)

$(MINIFB_ARCHIVE):
	@$(call download-library,https://codeload.github.com/emoon/minifb/tar.gz/$(MINIFB_REV))

$(MINIFB_STAMP): $(MINIFB_ARCHIVE)
	@mkdir -p $(MINIFB_SRC)
	@tar -xzf $< --strip-components=1 -C $(MINIFB_SRC)
	@touch $@

$(GRAPHICS_LIB): $(MINIFB_STAMP) src/main/c/simple_minifb.c src/main/c/simple_graphics.h graphics.mk
	@echo "building " $@
	@mkdir -p $(dir $@)
	@$(GRAPHICS_CC) -shared -O2 -Wall -Wextra -Wno-unused-parameter -Wno-implicit-fallthrough $(MINIFB_FLAGS) \
        -I$(MINIFB_SRC)/include -I$(MINIFB_SRC)/src \
        src/main/c/simple_minifb.c $(MINIFB_SOURCES) $(MINIFB_LIBS) -o $@
	@cp $(MINIFB_SRC)/LICENSE $(dir $@)MiniFB-LICENSE

# This executable also builds without MiniFB, to exercise the missing-backend path.
graphics-example: $(GRAPHICS_EXAMPLE)
$(GRAPHICS_EXAMPLE): docs/examples/Graphics.smp $(CLZDIR)/test/.ttag $(RELEASE_SYS) $(RELEASE_CRT) graphics.mk
	@$(JVM) com.seaofnodes.simple.Simple --link --cpu $(CPU_PORT) --abi $(CALL_CONV) -L $(dir $(RELEASE_SYS)) -o $(GAME_DIR)/Graphics.o $<

# An interactive target, never part of default/tests.
run-graphics: graphics graphics-example
	SIMPLE_MINIFB="$(abspath $(GRAPHICS_LIB))" $(GRAPHICS_EXAMPLE)
