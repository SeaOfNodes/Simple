#include <stdio.h>
#include <stdbool.h>

typedef struct AryInt {
    unsigned int len;
    unsigned char data[];
} AryInt;

extern AryInt* brain_fuck();

int main() {
  AryInt* ps = brain_fuck();

    printf("%.*s", ps->len, ps->data);

  return 0;
}