#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>

__attribute__ ((CALL_CONV))
extern int stack13(int x);

int main() {
int result = stack13(0);

printf("%d", result);
return 0;
}