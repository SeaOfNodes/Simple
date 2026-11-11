#include "simple_graphics.h"
#include <MiniFB.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <errno.h>
#include <time.h>
#endif

// One fixed-size window for now. No Simple pointer survives a native call:
// MiniFB retains its last draw buffer for OS repaint events, so copy it here.
static struct mfb_window *window;
static uint32_t *frame;
static uint32_t width, height;

// The C-only build never installs the upstream C++ callback wrappers.
void release_cpp_stub(struct mfb_window *unused) { (void)unused; }

static void close_window(void) {
    if (window) {
        mfb_close(window);             // Requests close.
        mfb_update_events(window);     // Actually destroys the window.
        window = NULL;
    }
    free(frame);
    frame = NULL;
}

static int32_t open_window(const char *title, uint32_t w, uint32_t h) {
    if (window || !w || !h || w > UINT32_MAX / 4 ||
        (uint64_t)w * h > UINT32_MAX || (uint64_t)w * h > SIZE_MAX / 4) {
        fprintf(stderr, "Simple graphics: invalid dimensions or a window is already open.\n");
        return -1;
    }
    frame = calloc((size_t)w * h, sizeof(*frame));
    if (!frame) {
        fprintf(stderr, "Simple graphics: cannot allocate the display buffer.\n");
        return -1;
    }
    window = mfb_open(title, w, h);
    if (!window) {
        fprintf(stderr, "Simple graphics: MiniFB could not open a window (is a graphical display available?).\n");
        close_window();
        return -1;
    }
    width = w;
    height = h;
    return 0;
}

static int32_t status(mfb_update_state state) {
    if (state == MFB_STATE_OK) return 0;
    if (state == MFB_STATE_EXIT) {
        window = NULL; // MiniFB has already destroyed it.
        close_window();
        return 1;
    }
    fprintf(stderr, "Simple graphics: MiniFB update failed (%d).\n", state);
    close_window();
    return -1;
}

static int32_t present(const uint32_t *pixels, uint32_t count) {
    if (!window) return 1;
    if (!pixels || count != (uint64_t)width * height) {
        fprintf(stderr, "Simple graphics: pixel array length must equal width * height.\n");
        return -1;
    }
    memcpy(frame, pixels, (size_t)count * sizeof(*frame));
    return status(mfb_update_ex(window, frame, width, height));
}

static int32_t poll_events(int32_t wait) {
    if (!window) return 1;
    if (wait) {
        // mfb_wait_sync returns false for both destruction and errors that
        // leave the window alive. Use a short OS wait, then the status-bearing
        // event API, so we know whether the window still needs destruction.
#ifdef _WIN32
        Sleep(17);
#else
        struct timespec delay = {0, 16666667};
        while (nanosleep(&delay, &delay) < 0) {
            if (errno != EINTR) {
                perror("Simple graphics: event wait");
                close_window();
                return -1;
            }
        }
#endif
    }
    return status(mfb_update_events(window));
}
static int32_t mouse_x(void) { return window ? mfb_get_mouse_x(window) : 0; }
static int32_t mouse_y(void) { return window ? mfb_get_mouse_y(window) : 0; }
static int32_t button(int32_t b) {
    return window && b >= 0 && b < 8 ? mfb_get_mouse_button_buffer(window)[b] != 0 : 0;
}
static int32_t key(int32_t k) {
    return window && k >= 0 && k <= MFB_KB_KEY_LAST ? mfb_get_key_buffer(window)[k] != 0 : 0;
}

static const struct simple_graphics_api api = {
    SIMPLE_GRAPHICS_VERSION, open_window, present, poll_events,
    mouse_x, mouse_y, button, key, close_window
};
#ifdef _WIN32
__declspec(dllexport)
#endif
const struct simple_graphics_api *simple_minifb_api(void) { return &api; }
