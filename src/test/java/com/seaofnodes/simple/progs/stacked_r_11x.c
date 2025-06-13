#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>

__attribute__ ((CALL_CONV))
extern int stack11(int x);

int main() {
int result = stack11(1);

printf("%d", result);
return 0;
}