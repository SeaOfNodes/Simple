# Run chapter targets from the repository root. Override CHAPTERS for a review batch:
#   make tests CHAPTERS="chapter21 chapter22"
#   make -k tests   # continue through failures, but return a failing status
.DEFAULT_GOAL := tests
CHAPTERS ?= $(sort $(patsubst %/pom.xml,%,$(wildcard chapter[0-9][0-9]/pom.xml)))
ACTIONS := tests tags release lib
CHAPTER_TARGETS := $(foreach action,$(ACTIONS),$(addsuffix /$(action),$(CHAPTERS)))

# Keep baseline/review runs sequential, including when invoked with -j.
.NOTPARALLEL:
.PHONY: $(ACTIONS) tag $(CHAPTER_TARGETS)
tag: tags
$(foreach action,$(ACTIONS),$(eval $(action): $(addsuffix /$(action),$(CHAPTERS))))

$(CHAPTER_TARGETS):
	+$(MAKE) --no-print-directory -C $(dir $@) $(notdir $@)
