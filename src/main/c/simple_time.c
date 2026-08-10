#define _POSIX_C_SOURCE 200809L
#include <time.h>
#include <stdint.h>
#include <errno.h>

// Fixed-width bridge; no native struct layout or retained Simple pointers.
static int64_t clock_value(clockid_t clock, int64_t scale) {
    struct timespec t;
    if (clock_gettime(clock,&t)) return -1;
    if (t.tv_sec>INT64_MAX/scale || t.tv_sec<INT64_MIN/scale) {
        errno=EOVERFLOW; return -1;
    }
    int64_t base=(int64_t)t.tv_sec*scale;
    int64_t fraction=t.tv_nsec/(1000000000/scale);
    if (base>INT64_MAX-fraction) { errno=EOVERFLOW; return -1; }
    return base+fraction;
}
int64_t simple_time_now(void) { return clock_value(CLOCK_REALTIME,1000); }
int64_t simple_time_mono(void) { return clock_value(CLOCK_MONOTONIC,1000); }
int64_t simple_time_nano(void) { return clock_value(CLOCK_MONOTONIC,1000000000); }
int32_t simple_time_sleep(int64_t ms, int32_t absolute) {
    if (ms<0) { errno=EINVAL; return -1; }
    struct timespec t={ms/1000,(ms%1000)*1000000};
    int result;
    if (!absolute) {
        // Retry the remaining duration after signals; no busy waiting.
        do { result=nanosleep(&t,&t); } while (result && errno==EINTR);
        return result;
    }
    do { result=clock_nanosleep(CLOCK_MONOTONIC,TIMER_ABSTIME,&t,NULL); } while (result==EINTR);
    if (result) { errno=result; return -1; }
    return 0;
}
static int leap(int y) { return y%4==0 && (y%100!=0 || y%400==0); }
static int local_fields(int64_t ms, struct tm *local, struct tm *utc) {
    tzset();
    int64_t sec=ms/1000;
    if (ms%1000<0) --sec;
    time_t t=(time_t)sec;
    if ((int64_t)t!=sec || !localtime_r(&t,local) || !gmtime_r(&t,utc)) return -1;
    if (local->tm_year < -1899 || local->tm_year > 8099) { errno=EOVERFLOW; return -1; }
    return 0;
}
int32_t simple_time_local(int64_t ms, int64_t *out) {
    struct tm t,u;
    if (local_fields(ms,&t,&u)) return -1;
    int days=t.tm_yday-u.tm_yday;
    if (t.tm_year>u.tm_year) days+=365+leap(u.tm_year+1900);
    if (t.tm_year<u.tm_year) days-=365+leap(t.tm_year+1900);
    int offset=days*86400+(t.tm_hour-u.tm_hour)*3600+(t.tm_min-u.tm_min)*60+t.tm_sec-u.tm_sec;
    out[0]=t.tm_year+1900; out[1]=t.tm_mon+1; out[2]=t.tm_mday;
    out[3]=t.tm_hour; out[4]=t.tm_min; out[5]=t.tm_sec;
    out[6]=(ms%1000+1000)%1000; out[7]=t.tm_wday;
    out[8]=offset; out[9]=t.tm_isdst>0;
    return 0;
}
int32_t simple_time_zone(int64_t ms, char *out, uint32_t capacity) {
    struct tm t,u;
    if (!capacity || local_fields(ms,&t,&u)) return -1;
    return (int32_t)strftime(out,capacity,"%Z",&t);
}
