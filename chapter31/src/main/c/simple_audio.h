#ifndef SIMPLE_AUDIO_H
#define SIMPLE_AUDIO_H
#include <stdint.h>

// Main-thread API. Samples are copied; no Simple heap pointer is retained.
struct simple_audio_api {
    int32_t version;
    int32_t (*open)(void);
    int32_t (*load)(const int16_t *, uint32_t);
    int32_t (*play)(int32_t);
    int32_t (*stop)(void);
    int32_t (*playing)(void);
    void (*close)(void);
};
#define SIMPLE_AUDIO_VERSION 1
#endif
