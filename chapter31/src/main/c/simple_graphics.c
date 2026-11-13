#define _POSIX_C_SOURCE 200809L
#include "simple_graphics.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>

#ifdef __CYGWIN__
#define BACKEND_NAME "simple_minifb.dll"
#define BACKEND_ABI "x86_64_v2_win64"
#else
#define BACKEND_NAME "libsimple_minifb.so"
#define BACKEND_ABI "x86_64_v2_SystemV"
#endif

// Executable-relative paths work even when launched outside the checkout.
// Working-directory paths also cover test executables and chapter/root shells.
static void *find_backend(void) {
    char executable[4096], path[8192];
    ssize_t n=readlink("/proc/self/exe",executable,sizeof(executable)-1);
    if (n>0 && n<(ssize_t)sizeof(executable)-1) {
        executable[n]=0;
        char *slash=strrchr(executable,'/');
        if (slash) {
            *slash=0;
            const char *relative[]={"/" BACKEND_NAME,
                "/../../release/" BACKEND_ABI "/" BACKEND_NAME,
                "/../release/" BACKEND_ABI "/" BACKEND_NAME};
            for (unsigned i=0;i<sizeof(relative)/sizeof(*relative);++i) {
                snprintf(path,sizeof(path),"%s%s",executable,relative[i]);
                void *handle=dlopen(path,RTLD_NOW | RTLD_LOCAL);
                if (handle) return handle;
            }
        }
    }
    const char *relative[]={"./" BACKEND_NAME,
        "./build/release/" BACKEND_ABI "/" BACKEND_NAME,
        "./chapter31/build/release/" BACKEND_ABI "/" BACKEND_NAME};
    for (unsigned i=0;i<sizeof(relative)/sizeof(*relative);++i) {
        void *handle=dlopen(relative[i],RTLD_NOW | RTLD_LOCAL);
        if (handle) return handle;
    }
    return NULL;
}

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
    int explicit_path=path && *path;
    library = explicit_path ? dlopen(path, RTLD_NOW | RTLD_LOCAL) : find_backend();
    if (!library) {
        if (explicit_path)
            fprintf(stderr, "Simple graphics: cannot load MiniFB backend '%s': %s\n", path, dlerror());
        else
            fprintf(stderr, "Simple graphics: MiniFB backend not found beside the executable or in build/release/"
                    BACKEND_ABI ". Run make graphics, or set SIMPLE_MINIFB to its full path.\n");
        return -1;
    }
    if (!explicit_path) path="automatically discovered backend";
    const struct simple_graphics_api *(*get_api)(void) =
        (const struct simple_graphics_api *(*)(void))dlsym(library, "simple_minifb_api");
    const struct simple_graphics_api *candidate = get_api ? get_api() : NULL;
    if (!candidate || candidate->version != SIMPLE_GRAPHICS_VERSION ||
        !candidate->open || !candidate->present || !candidate->poll ||
        !candidate->mouse_x || !candidate->mouse_y || !candidate->button ||
        !candidate->key || !candidate->close || !candidate->wait) {
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
int32_t simple_gfx_wait(int64_t timeout) { return api ? api->wait(timeout) : -1; }
