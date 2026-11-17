#include <stdint.h>
#include <stdio.h>

typedef struct { int32_t x, y; } Pair;
typedef struct { uint32_t len; int32_t values[]; } Ints;

__attribute__((CALL_CONV)) extern int64_t int_echo(int32_t);
__attribute__((CALL_CONV)) extern int64_t int_wrap(int32_t);
__attribute__((CALL_CONV)) extern int64_t wide_echo(int64_t);
__attribute__((CALL_CONV)) extern int64_t narrow_sum(int8_t,uint8_t,int16_t,uint16_t,int32_t,uint32_t,int32_t,uint32_t);
__attribute__((CALL_CONV)) extern Pair *int_pair(void);
__attribute__((CALL_CONV)) extern Ints *int_array(void);

int main(void) {
    if (int_echo(-1) != -1 || int_echo(INT32_MIN) != INT32_MIN) return 1;
    if (int_wrap(INT32_MAX) != INT32_MIN) return 2;
    if (wide_echo(INT64_MAX) != INT64_MAX || wide_echo(INT64_MIN) != INT64_MIN) return 3;
    int64_t expected = -128LL + 255 - 32768 + 65535 + INT32_MIN + UINT32_MAX - 7 + UINT32_MAX;
    if (narrow_sum(-128,255,-32768,65535,INT32_MIN,UINT32_MAX,-7,UINT32_MAX) != expected) return 4;
    Pair *p = int_pair();
    if (p->x != -1 || p->y != INT32_MAX) return 5;
    Ints *a = int_array();
    if (a->len != 3 || a->values[0] != -1 || a->values[1] != INT32_MAX || a->values[2] != INT32_MIN) return 6;
    puts("ok");
    return 0;
}
