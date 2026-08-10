// Explicit optional integration checks: real miniaudio thread, silent device.
#define SIMPLE_AUDIO_TEST_NULL
#include "../../main/c/simple_miniaudio.c"
#include <assert.h>

int main(void) {
    assert(api.play(1)==-1 && api.playing()==0);
    assert(api.stop()==0);
    api.close();
    for (int pass=0;pass<2;++pass) {
        assert(api.open()==0 && api.open()==0);
        assert(api.load(NULL,1)==-1);
        int16_t pcm[RATE]={0};
        assert(api.load(pcm,0)==-1 && api.load(pcm,RATE*10+1)==-1);
        int sound=api.load(pcm,RATE);
        assert(sound==4);
        pcm[0]=12345;
        assert(sounds[sound].pcm[0]==0);
        assert(api.play(-1)==-1 && api.play(0)==-1 && api.play(SLOTS)==-1);
        assert(api.play(sound)==0 && api.playing());
        // A one-second sound returns while playback is still in progress.
        assert(api.stop()==0 && !api.playing());
        for (int cue=1;cue<=3;++cue) {
            assert(api.play(cue)==0);
            int elapsed=0;
            while (api.playing() && elapsed<2000) { ma_sleep(10); elapsed+=10; }
            assert(!api.playing());
        }
        for (int i=5;i<SLOTS;++i) assert(api.load(pcm,1)==i);
        assert(api.load(pcm,1)==-1);
        assert(api.play(sound)==0);
        api.close(); // Join during playback, then reopen next pass.
        api.close();
        assert(api.play(sound)==-1 && !api.playing());
    }
    // The loss cue reverses exactly the same three softly enveloped notes.
    assert(tone(1,0)==0 && tone(2,0)==0 && tone(3,1)==0);
    uint32_t note=RATE*16/100;
    for (int n=0;n<3;++n)
        assert(memcmp(sounds[2].pcm+n*note,sounds[3].pcm+(2-n)*note,note*sizeof(int16_t))==0);
    for (int i=1;i<=3;++i) {
        assert(sounds[i].pcm[0]==0);
        for (uint32_t j=0;j<sounds[i].count;++j) assert(abs(sounds[i].pcm[j])<=3200);
        free(sounds[i].pcm); sounds[i].pcm=NULL;
    }
    puts("audio native checks passed");
    return 0;
}
