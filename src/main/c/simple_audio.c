#include "simple_audio.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

static const struct simple_audio_api *api;
static void *library;

int32_t simple_audio_open(void) {
    const char *path = getenv("SIMPLE_MINIAUDIO");
    if (path && strcmp(path,"off")==0) return -1;
    if (!api) {
        if (!path || !*path) return -1; // Optional: games may run silently.
        library = dlopen(path,RTLD_NOW | RTLD_LOCAL);
        if (!library) {
            fprintf(stderr,"Simple audio: cannot load backend '%s': %s\n",path,dlerror());
            return -1;
        }
        const struct simple_audio_api *(*get_api)(void) =
            (const struct simple_audio_api *(*)(void))dlsym(library,"simple_miniaudio_api");
        const struct simple_audio_api *candidate = get_api ? get_api() : NULL;
        if (!candidate || candidate->version!=SIMPLE_AUDIO_VERSION || !candidate->open ||
            !candidate->load || !candidate->play || !candidate->stop ||
            !candidate->playing || !candidate->close) {
            fprintf(stderr,"Simple audio: incompatible backend; rebuild with make audio.\n");
            dlclose(library);
            library=NULL;
            return -1;
        }
        api=candidate;
        atexit(api->close);
    }
    return api->open();
}
int32_t simple_audio_load(const int16_t *samples, uint32_t count) { return api ? api->load(samples,count) : -1; }
int32_t simple_audio_play(int32_t sound) { return api ? api->play(sound) : -1; }
int32_t simple_audio_stop(void) { return api ? api->stop() : 0; }
int32_t simple_audio_playing(void) { return api ? api->playing() : 0; }
int32_t simple_audio_close(void) { if (api) api->close(); return 0; }
