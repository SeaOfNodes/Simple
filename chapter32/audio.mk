# Optional miniaudio, independent of the default build and MiniFB.
MINIAUDIO_REV := 0.11.23
MINIAUDIO_SRC := lib/miniaudio-$(MINIAUDIO_REV)
MINIAUDIO_HEADER := $(MINIAUDIO_SRC)/miniaudio.h
ifeq ($(OS),Windows_NT)
    AUDIO_CC := x86_64-w64-mingw32-gcc
    AUDIO_LIB := $(RELEASE)/$(CPU_ABI)/simple_miniaudio.dll
    AUDIO_FLAGS := -D_WIN32_WINNT=0x0601
    AUDIO_LIBS := -lm
else
    AUDIO_CC := gcc
    AUDIO_LIB := $(RELEASE)/$(CPU_ABI)/libsimple_miniaudio.so
    AUDIO_FLAGS := -fPIC
    AUDIO_LIBS := -lpthread -lm -ldl
endif
.PHONY: audio
audio: $(AUDIO_LIB)
.PHONY: test-audio
test-audio: $(MINIAUDIO_HEADER)
	@mkdir -p build/objs
	@$(AUDIO_CC) -O2 -Wall -Wextra $(AUDIO_FLAGS) -I$(MINIAUDIO_SRC) src/test/c/audio_test.c $(AUDIO_LIBS) -o build/objs/audio_test$(if $(filter Windows_NT,$(OS)),.exe,)
	@build/objs/audio_test$(if $(filter Windows_NT,$(OS)),.exe,)
lib libs init: $(MINIAUDIO_HEADER)

$(MINIAUDIO_HEADER):
	@$(call download-library,https://raw.githubusercontent.com/mackron/miniaudio/$(MINIAUDIO_REV)/miniaudio.h)
$(AUDIO_LIB): $(MINIAUDIO_HEADER) src/main/c/simple_miniaudio.c src/main/c/simple_audio.h audio.mk
	@echo "building " $@
	@mkdir -p $(dir $@)
	@$(AUDIO_CC) -shared -O2 -Wall -Wextra $(AUDIO_FLAGS) -I$(MINIAUDIO_SRC) src/main/c/simple_miniaudio.c $(AUDIO_LIBS) -o $@
	@sed -n '/^This software is available as a choice of the following licenses/,$$p' $(MINIAUDIO_HEADER) > $(dir $@)miniaudio-LICENSE
