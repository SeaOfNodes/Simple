#include <stdint.h>
#include <stdio.h>

int32_t counter=7;
int8_t small=-3;
double fraction=1.25;

int64_t bump(void) { counter+=5; return counter; }
__attribute__((CALL_CONV)) extern int64_t test_global(void);

int main(void) {
    int64_t result=test_global();
    if(result!=71515 || counter!=15 || small!=-2 || fraction!=1.75) {
        printf("result=%lld counter=%d small=%d fraction=%g\n",
               (long long)result,counter,small,fraction);
        return 1;
    }
    // A second call must observe C's new values, not a Simple initialization copy.
    counter=-2;
    result=test_global();
    return result!=-19394 || counter!=6 || small!=-1 || fraction!=2.25;
}
