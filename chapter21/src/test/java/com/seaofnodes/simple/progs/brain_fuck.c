#include <stdio.h>
#include <stdbool.h>
#include <stdio.h>

typedef struct AryInt {
    unsigned int len;
    int data[];
} AryInt;

extern AryInt* brain_fuck();

int main() {
  AryInt* ps = brain_fuck();
  // Hello world in brainfuck
  for( int i=0; i<ps->len; i++ )
    putchar(ps->data[i]);
  return 0;
}