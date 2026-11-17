#include <stdio.h>
#include <stdbool.h>

typedef struct AryInt {
  unsigned int len;
  unsigned int data[];
} AryInt;

__attribute__ ((CALL_CONV))
extern AryInt *sieve(int N);

int main() {
  AryInt* ps = sieve(100);
  printf("%d[",ps->len);
  for( int i=0; i<ps->len; i++ )
    printf("%d, ",ps->data[i]);
  printf("]");
  return 0;
}
