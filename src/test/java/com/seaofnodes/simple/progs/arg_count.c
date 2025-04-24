#include <stdio.h>

__attribute__ ((CALL_CONV))
extern double addAll( long long i0, double f1, long long i2, double f3, long long i4, double f5, long long i6, double f7, long long i8, double f9, long long i10, double f11, long long i12, double f13, long long i14, double f15, long long i16, double f17, long long i18, double f19 );

int main() {
  double result = addAll( 0, 1.1, 2, 3.1, 4, 5.1, 6, 7.1, 8, 9.1, 10, 11.1, 12, 13.1, 14, 15.1, 16, 17.1, 18, 19.1 );
  printf("%f\n", result);
  return 0;
}
