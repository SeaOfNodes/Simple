#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>

extern int test();

int main() {
    int result = test();
    printf("d\n", result);
return 0;
}