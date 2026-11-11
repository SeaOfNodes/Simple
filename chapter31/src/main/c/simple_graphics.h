#ifndef SIMPLE_GRAPHICS_H
#define SIMPLE_GRAPHICS_H
#include <stdint.h>

// Private, versioned boundary between the tiny runtime loader and MiniFB.
// All calls run on the main thread. Pixel memory is borrowed for present only.
struct simple_graphics_api {
    int32_t version;
    int32_t (*open)(const char *, uint32_t, uint32_t);
    int32_t (*present)(const uint32_t *, uint32_t);
    int32_t (*poll)(int32_t);
    int32_t (*mouse_x)(void);
    int32_t (*mouse_y)(void);
    int32_t (*button)(int32_t);
    int32_t (*key)(int32_t);
    void (*close)(void);
};
#define SIMPLE_GRAPHICS_VERSION 1
#endif
