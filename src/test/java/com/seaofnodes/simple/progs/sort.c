#include <stdio.h>
#include <stdlib.h>

// Test wrapper to call `sort.smp`

typedef struct AryInt {
    unsigned int len;
    long long data[];
} AryInt;

__attribute__ ((CALL_CONV))
extern AryInt* merge_sort(AryInt*);


long long primes[] = { 25, 97, 89, 83, 79, 73, 71, 67, 61, 59, 53, 47, 43, 41, 37, 31, 29, 23, 19, 17, 13, 11, 7, 5, 3, 2 };

int main() {
  AryInt *as = (AryInt*)primes;

  merge_sort(as);

  printf("%d[",as->len);
  for( int i=0; i<as->len; i++ )
    printf("%d, ",as->data[i]);
  printf("]");

  return 0;
}