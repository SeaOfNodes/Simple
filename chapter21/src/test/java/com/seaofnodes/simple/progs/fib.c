#include <stdio.h>
#include <stdbool.h>


extern int fib(int x);

int main() {
  printf("[");
  for (int i = 0; i < 10; i++) {
    printf("%d", fib(i));
    if (i < 9) printf(", ");
  }
  printf("]");
  return 0;
}