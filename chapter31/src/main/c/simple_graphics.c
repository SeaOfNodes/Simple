#include "simple_graphics.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

// No MiniFB headers or link dependency in the default runtime. Cygwin's
// dlopen also loads native Windows DLLs; both use the x64 Windows C ABI.
static const struct simple_graphics_api *api;
static void *library;

static int load_graphics(void) {
    // Explicitly disabling graphics also makes headless tests deterministic.
    const char *path = getenv("SIMPLE_MINIFB");
    if (path && strcmp(path, "off") == 0) {
        fprintf(stderr, "Simple graphics: disabled (SIMPLE_MINIFB=off).\n");
        return -1;
    }
    if (api) return 0;
    if (!path || !*path) {
        fprintf(stderr, "Simple graphics: MiniFB backend not configured. "
                "Run make graphics, then set SIMPLE_MINIFB to the backend library's full path.\n");
        return -1;
    }
    library = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        fprintf(stderr, "Simple graphics: cannot load MiniFB backend '%s': %s\n", path, dlerror());
        return -1;
    }
    const struct simple_graphics_api *(*get_api)(void) =
        (const struct simple_graphics_api *(*)(void))dlsym(library, "simple_minifb_api");
    const struct simple_graphics_api *candidate = get_api ? get_api() : NULL;
    if (!candidate || candidate->version != SIMPLE_GRAPHICS_VERSION ||
        !candidate->open || !candidate->present || !candidate->poll ||
        !candidate->mouse_x || !candidate->mouse_y || !candidate->button ||
        !candidate->key || !candidate->close) {
        fprintf(stderr, "Simple graphics: incompatible MiniFB backend '%s'. Rebuild with make graphics.\n", path);
        dlclose(library);
        library = NULL;
        return -1;
    }
    api = candidate;
    // Keep the library loaded until process exit; its window callbacks may
    // still be registered. Closing a window releases its native resources.
    atexit(api->close);
    return 0;
}

int32_t simple_gfx_open(const char *title, uint32_t width, uint32_t height) {
    return load_graphics() ? -1 : api->open(title, width, height);
}
int32_t simple_gfx_present(const uint32_t *pixels, uint32_t count) {
    return api ? api->present(pixels, count) : -1;
}
int32_t simple_gfx_poll(int32_t wait) { return api ? api->poll(wait) : -1; }
int32_t simple_gfx_mouse_x(void) { return api ? api->mouse_x() : 0; }
int32_t simple_gfx_mouse_y(void) { return api ? api->mouse_y() : 0; }
int32_t simple_gfx_button(int32_t button) { return api ? api->button(button) : 0; }
int32_t simple_gfx_key(int32_t key) { return api ? api->key(key) : 0; }
int32_t simple_gfx_close(void) { if (api) api->close(); return 0; }
