# Dependency downloads are permitted only by an explicit setup goal.
# Keep this cache outside build/ so make clean does not erase it.
.PHONY: lib libs init
required_jars := lib/junit-4.12.jar lib/hamcrest-core-1.3.jar
lib libs init: $(required_jars)

# Chapters are self-contained, but identical JARs need not be downloaded again.
# Copy an existing repository/sibling cache even for an ordinary offline build.
cached-library = $(firstword $(filter-out $(abspath $@),$(abspath $(wildcard ../lib/$(notdir $@) ../chapter*/lib/$(notdir $@)))))

# The legacy `lib` name remains an explicit setup alias.
ifneq ($(filter lib libs init,$(MAKECMDGOALS)),)
define download-library
mkdir -p $(dir $@) && curl -fsSL $(1) -o $@.tmp && mv $@.tmp $@
endef
else
define download-library
echo "Missing cached dependency: $@. Run make libs (or make init) while online." >&2; exit 1
endef
endif

lib/junit-4.12.jar:
	@$(if $(cached-library),mkdir -p lib && cp "$(cached-library)" $@,$(call download-library,https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar))

lib/hamcrest-core-1.3.jar:
	@$(if $(cached-library),mkdir -p lib && cp "$(cached-library)" $@,$(call download-library,https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar))
