#include "simple_audio.h"
#define MA_NO_DECODING
#define MA_NO_ENCODING
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#define MINIAUDIO_IMPLEMENTATION
#include <miniaudio.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define RATE 48000
#define SLOTS 32
static ma_device device;
static ma_mutex mutex;
static int opened, active;
static uint32_t cursor;
static struct { int16_t *pcm; uint32_t count; } sounds[SLOTS];

// Only native-owned immutable PCM is read on the audio thread. The lock covers
// a bounded copy, never allocation, synthesis, device calls, or Simple code.
static void output(ma_device *unused, void *out, const void *in, ma_uint32 frames) {
    (void)unused; (void)in;
    memset(out,0,frames*sizeof(int16_t));
    ma_mutex_lock(&mutex);
    if (active) {
        uint32_t count=sounds[active].count-cursor;
        if (count>frames) count=frames;
        memcpy(out,sounds[active].pcm+cursor,count*sizeof(int16_t));
        cursor+=count;
        if (cursor==sounds[active].count) active=0;
    }
    ma_mutex_unlock(&mutex);
}
static int32_t load(const int16_t *pcm, uint32_t count) {
    if (!opened || !pcm || !count || count>RATE*10) return -1;
    int slot;
    for (slot=4; slot<SLOTS && sounds[slot].pcm; ++slot) {}
    if (slot==SLOTS) return -1;
    int16_t *copy=malloc(count*sizeof(*copy));
    if (!copy) return -1;
    memcpy(copy,pcm,count*sizeof(*copy));
    ma_mutex_lock(&mutex);
    sounds[slot].pcm=copy; sounds[slot].count=count;
    ma_mutex_unlock(&mutex);
    return slot;
}
static int32_t play(int32_t sound) {
    if (!opened || sound<=0 || sound>=SLOTS || !sounds[sound].pcm) return -1;
    ma_mutex_lock(&mutex);
    active=sound; cursor=0;
    ma_mutex_unlock(&mutex);
    return 0;
}
static int32_t stop(void) {
    if (opened) { ma_mutex_lock(&mutex); active=0; ma_mutex_unlock(&mutex); }
    return 0;
}
static int32_t playing(void) {
    if (!opened) return 0;
    ma_mutex_lock(&mutex);
    int result=active!=0;
    ma_mutex_unlock(&mutex);
    return result;
}
static void close_audio(void) {
    if (!opened) return;
    ma_device_uninit(&device); // Joins callback before freeing buffers/mutex.
    ma_mutex_uninit(&mutex);
    for (int i=1;i<SLOTS;++i) {
        free(sounds[i].pcm); sounds[i].pcm=NULL; sounds[i].count=0;
    }
    opened=0; active=0; cursor=0;
}
static int tone(int slot, int descending) {
    uint32_t note=slot==1 ? RATE*7/100 : RATE*16/100;
    int notes=slot==1 ? 1 : 3;
    sounds[slot].count=note*notes;
    sounds[slot].pcm=calloc(note*notes,sizeof(int16_t));
    if (!sounds[slot].pcm) return -1;
    const double pitches[3]={523.251,659.255,783.991};
    for (int n=0;n<notes;++n) {
        double hz=slot==1 ? 440.0 : pitches[descending ? 2-n : n];
        for (uint32_t i=0;i<note;++i) {
            double t=(double)i/note;
            double envelope=sin(3.141592653589793*t);
            sounds[slot].pcm[n*note+i]=(int16_t)(3200*envelope*envelope*sin(6.283185307179586*hz*i/RATE));
        }
    }
    return 0;
}
static int32_t open_audio(void) {
    if (opened) return 0;
    if (ma_mutex_init(&mutex)!=MA_SUCCESS) return -1;
    ma_device_config config=ma_device_config_init(ma_device_type_playback);
    config.playback.format=ma_format_s16;
    config.playback.channels=1;
    config.sampleRate=RATE;
    config.dataCallback=output;
#ifdef SIMPLE_AUDIO_TEST_NULL
    ma_backend backend=ma_backend_null;
    ma_result result=ma_device_init_ex(&backend,1,NULL,&config,&device);
#else
    ma_result result=ma_device_init(NULL,&config,&device);
#endif
    if (result!=MA_SUCCESS) {
        ma_mutex_uninit(&mutex);
        fprintf(stderr,"Simple audio: no playback device (%d); continuing silently.\n",result);
        return -1;
    }
    opened=1;
    if (tone(1,0) || tone(2,0) || tone(3,1) || ma_device_start(&device)!=MA_SUCCESS) {
        close_audio(); return -1;
    }
    return 0;
}
static const struct simple_audio_api api={SIMPLE_AUDIO_VERSION,open_audio,load,play,stop,playing,close_audio};
#ifdef _WIN32
__declspec(dllexport)
#endif
const struct simple_audio_api *simple_miniaudio_api(void) { return &api; }
