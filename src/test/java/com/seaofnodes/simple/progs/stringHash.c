#include <stdio.h>
#include <stdbool.h>

typedef struct ArrayU8 {
  unsigned int length;
  unsigned char data[];
} ArrayU8;
typedef struct String {
  ArrayU8 * cs;
  long long _hashCode;
} String;


__attribute__ ((CALL_CONV))
extern bool equals(String*st1, String*st2);

int main() {
  String test1 = {.cs=(ArrayU8*)"\x04\x00\x00\x00test"};
  String test2 = {.cs=(ArrayU8*)"\x05\x00\x00\x00test1"};

  if( equals(&test1, &test2) ) {
    printf("Expected test1 and test2 unequal");
    return 1;
  }
  if( !equals(&test2, &test2) ) {
    printf("Expected test2 and test2 equal");
    return 1;
  }
  return 0;
}
