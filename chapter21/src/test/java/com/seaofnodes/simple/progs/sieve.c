#include <stdio.h>
#include <stdbool.h>

typedef struct AryInt {
  unsigned int len;
  unsigned int data[];
} AryInt;

extern AryInt sieve(int N);

int main() {
  AryInt ps = sieve(10);
  printf("[");
  for( int i=0; i<ps.len; i++ )
    printf("%d, ",ps.data[i]);
  printf("]");
  return 0;
}
