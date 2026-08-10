#include <stdint.h>
#include <stdio.h>

int32_t counter=7;
__attribute__((CALL_CONV)) extern int64_t test_global(void);

int main(void) {
    int64_t result=test_global();
    if(result!=710 || counter!=10) {
        printf("result=%lld counter=%d\n",(long long)result,counter);
        return 1;
    }
    return 0;
}
