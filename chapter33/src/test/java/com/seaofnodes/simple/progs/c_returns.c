#include <stdint.h>
#include <stdio.h>

uint64_t c_bits;
// Legal narrow C returns: unused RAX bits deliberately contain garbage.
#define RET(name) __asm__(".text\n.globl " #name "\n" #name ":\nmovq c_bits(%rip), %rax\nret\n");
RET(c_i8) RET(c_u8) RET(c_i16) RET(c_u16) RET(c_i32) RET(c_u32) RET(c_i64)
#define DECL(name) extern int64_t __attribute__((CALL_CONV)) name(void);
DECL(test_i8) DECL(test_u8) DECL(test_i16) DECL(test_u16)
DECL(test_i32) DECL(test_u32) DECL(test_i64) DECL(test_cmp)

int main(void) {
    uint64_t values[] = {0xa5a5a5a500000000ULL, 0xa5a5a5a50000007fULL,
        0xa5a5a5a500000080ULL, 0xa5a5a5a500007fffULL, 0xa5a5a5a500008000ULL,
        0xa5a5a5a57fffffffULL, 0xa5a5a5a580000000ULL, 0xa5a5a5a5ffffffffULL};
    for(unsigned i=0; i<sizeof(values)/sizeof(values[0]); i++) {
        c_bits=values[i];
        int64_t actual[]={test_i8(),test_u8(),test_i16(),test_u16(),test_i32(),test_u32(),test_i64(),test_cmp()};
        int64_t expected[]={(int8_t)c_bits,(uint8_t)c_bits,(int16_t)c_bits,(uint16_t)c_bits,
                            (int32_t)c_bits,(uint32_t)c_bits,(int64_t)c_bits,(int32_t)c_bits==-1};
        for(unsigned j=0; j<8; j++)
            if(actual[j]!=expected[j]) {
                printf("case %u type %u: %lld != %lld\n",i,j,(long long)actual[j],(long long)expected[j]);
                return 1;
            }
    }
    return 0;
}
